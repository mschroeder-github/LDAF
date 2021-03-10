package de.dfki.sds.ldaf;

import com.r6lab.sparkjava.jwt.controller.AuthController;
import com.r6lab.sparkjava.jwt.user.User;
import com.r6lab.sparkjava.jwt.user.UserService;
import de.dfki.sds.ldaf.rdf.RDFDatasetStorage;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.compose.MultiUnion;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.system.Txn;
import org.apache.jena.vocabulary.RDF;
import org.commonjava.mimeparse.MIMEParse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.template.freemarker.FreeMarkerEngine;

/**
 * Abstract resource to handle linked data.
 */
public abstract class LinkedDataResource {

    private static final String SPARQL_PATH = "/de/dfki/sds/ldaf/sparql";
    private static final String QUERY_PARAM_PREFIX = "query_";
    private static final boolean DEBUG_SPARQL = false;

    private static int defaultLimit = 4 * 4;
    
    protected LinkedDataApplication ldaf;
    protected RDFDatasetStorage datasetStorage;
    protected FreeMarkerEngine freeMarkerEngine;
    protected String serverAddress;
    protected Counter counter;
    protected Converter converter;
    protected UserService userService;
    protected AuthController authController;
    
    protected Comparator<Resource> resourceComparator = (Resource a, Resource b) -> {
        
        int ia = a.getURI().lastIndexOf("/");
        int ib = b.getURI().lastIndexOf("/");
        
        if(ia != -1 && ib != -1) {
            String lna = a.getURI().substring(ia + 1);
            String lnb = b.getURI().substring(ib + 1);
            if(lna.matches("\\d+") && lnb.matches("\\d+")) {
                return Integer.compare(Integer.parseInt(lna), Integer.parseInt(lnb));
            }
        }
        
        return a.getURI().compareTo(b.getURI());
    };

    //override this to init something for the resource
    public void init() {

    }

    //==========================================================================
    
    /**
     * Visible is all public graphs (settings), user graph, ontology (via supplier), given additional models.
     * @param req
     * @param additionalModels
     * @return 
     */
    protected Model getUnionOfVisibleModels(Request req, Model... additionalModels) {
        List<org.apache.jena.graph.Graph> graphs = new ArrayList<>();
        for(String graphURI : ldaf.getSettings().getPublicGraphs()) {
            graphs.add(datasetStorage.getGraph(graphURI).getModel().getGraph());
        }
        graphs.add(getUserGraph(req).getModel().getGraph());
        graphs.add(ldaf.getSettings().getOntologySupplier().get().getGraph());
        for(Model m : additionalModels) {
            graphs.add(m.getGraph());
        }
        return ModelFactory.createModelForGraph(new MultiUnion(graphs.toArray(new org.apache.jena.graph.Graph[0])));
    }
    
    protected RDFGraphStorage getUserGraph(Request req) {
        String username = authController.getUserNameFromToken(req);
        String graphURI = getUserGraphUri(username);
        return datasetStorage.getGraph(graphURI);
    }
    
    protected RDFGraphStorage getUserGraph(String username) {
        String graphURI = getUserGraphUri(username);
        return datasetStorage.getGraph(graphURI);
    }
    
    protected void defaultRoutes(String restResourceName, Function<Request, RDFGraphStorage> graphFunction, Resource type, Influence influence) {
        defaultRoutes(restResourceName, graphFunction, type, true, influence);
    }
    
    protected void defaultRoutes(String restResourceName, Function<Request, RDFGraphStorage> graphFunction, Resource type) {
        defaultRoutes(restResourceName, graphFunction, type, true, null);
    }
    
    protected void defaultRoutes(String restResourceName, Function<Request, RDFGraphStorage> graphFunction, Resource type, boolean postUsesAutoincIds, Influence influence) {
        String name = restResourceName;
        String prefix = "/" + name;

        //TODO later also add the Influence object to the other default* methods
        
        Spark.get(prefix, (req, resp) -> this.defaultGetList(req, resp, graphFunction, type, name + "list.html", influence));
        Spark.get(prefix + "/:id", (req, resp) -> this.defaultGet(req, resp, graphFunction, name + ".html"));
        
        Spark.post(prefix, (req, resp) -> this.defaultPost(req, resp, graphFunction, type, name, postUsesAutoincIds));
        Spark.put(prefix + "/:id", (req, resp) -> this.defaultPut(req, resp, graphFunction));
        Spark.patch(prefix + "/:id", (req, resp) -> this.defaultPatch(req, resp, graphFunction));
        Spark.delete(prefix + "/:id", (req, resp) -> this.defaultDelete(req, resp, graphFunction));
    }
    
    protected void defaultModifyRoutes(String restResourceName, Function<Request, RDFGraphStorage> graphFunction, Resource type, boolean postUsesAutoincIds) {
        String name = restResourceName;
        String prefix = "/" + name;
        Spark.post(prefix, (req, resp) -> this.defaultPost(req, resp, graphFunction, type, name, postUsesAutoincIds));
        Spark.put(prefix + "/:id", (req, resp) -> this.defaultPut(req, resp, graphFunction));
        Spark.patch(prefix + "/:id", (req, resp) -> this.defaultPatch(req, resp, graphFunction));
        Spark.delete(prefix + "/:id", (req, resp) -> this.defaultDelete(req, resp, graphFunction));
    }

    protected Object defaultGet(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction, String htmlTemplateName) {
        Resource res = resource(req);

        RDFGraphStorage storage = graphFunction.apply(req);
        
        int depth = Integer.parseInt(req.queryParamOrDefault("depth", "1"));
        
        JSONObject obj = storage.calculateRead(() -> {
            boolean contains = storage.getModel().containsResource(res);
            if (!contains) {
                return null;
            }

            //get model about subject
            return converter.toJSON(res, getUnionOfVisibleModels(req), depth);
        });

        //if model is null there is no resource found
        if (obj == null) {
            Spark.halt(HttpStatus.NOT_FOUND_404, "resource not found");
        }

        //convert to json
        //JSONObject obj = converter.toJSON(res, model);
        //response based on accept header
        return response(req, resp,
                render -> {
                    
                    //direct access data
                    render.putAll(converter.toRenderModel(obj));
                    render.put("jsonsrc", obj.toString());
                    
                    
                    
                    //if game.html you have "game" as resource name
                    //render.put(FilenameUtils.getBaseName(htmlTemplateName), converter.toRenderModel(obj));
                    
                    return htmlTemplateName;
                },
                json -> copy(obj, json),
                mod -> {
                    mod.setNsPrefixes(PrefixMapping.Standard);
                    mod.setNsPrefix(ldaf.getSettings().getPrefix(), ldaf.getSettings().getOntologyNamespace());
                    Txn.executeRead(datasetStorage.getDataset(), () -> {
                        mod.add(modelAbout(res, getUnionOfVisibleModels(req)));
                    });
                }
        );
    }

    protected Object defaultGetList(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction, Resource type, String htmlTemplateName, Influence influence) {
        RDFGraphStorage storage = graphFunction.apply(req);
        
        List<Resource> resources = storage.calculateRead(() -> {
            return storage.getModel().listResourcesWithProperty(RDF.type, type).toList();
        });

        //sort by uri
        Collections.sort(resources, resourceComparator);
        Collections.reverse(resources);
        
        //to json
        JSONObject result = new JSONObject();
        JSONUtils.forceLinkedHashMap(result);
        
        List<Resource> sublist;
        
        if(influence != null && influence.isNoSublistInGetList()) {
            sublist = resources;
            //MAYBE could also add the other properties
            result.put("total", sublist.size());
        } else {
            sublist = calculateList(req, resources, result);
        }
        
        JSONArray array = new JSONArray();
        storage.executeRead(() -> {
            Model union = getUnionOfVisibleModels(req);
            for(Resource res : sublist) {
                array.put(converter.toJSON(res, union, 1));
            }
        });
        result.put("list", array);
        
        //change the json result with your influence object
        if(influence != null && influence.getConsumeResultInGetList() != null) {
            influence.getConsumeResultInGetList().accept(result);
        }
        
        return response(req, resp,
                render -> {
                    prepareRenderModel(render, result, req);
                    
                    return htmlTemplateName;
                },
                json -> {
                    copy(result, json);
                },
                mod -> {
                    for(Resource res : resources) {
                        mod.add(res, RDF.type, type);
                    }
                }
        );
    }
    
    protected void prepareRenderModel(RenderModel render, JSONObject result, Request req) {
        //direct access data
        render.putAll(converter.toRenderModel(result));
        render.put("jsonsrc", result.toString());

        //add query params too
        for(String queryParam : req.queryParams()) {
            render.put(QUERY_PARAM_PREFIX + queryParam, req.queryParams(queryParam));
        }
    }

    /**
     * Calculates list from given list. 
     * Checks in req for offset and limit query parameters.
     * List has generic types.
     * Puts in result total, shown, offset, limit, page, pages, next, prev information.
     * Returns sublist.
     * @param <T>
     * @param req
     * @param resources
     * @param result
     * @return 
     */
    protected <T> List<T> calculateList(Request req, List<T> resources, JSONObject result) {
        int total = resources.size();
        int offset = Integer.parseInt(req.queryParamOrDefault("offset", "0"));
        if(offset < 0) {
            offset = 0;
        }
        int limit = Integer.parseInt(req.queryParamOrDefault("limit", String.valueOf(defaultLimit)));
        int pages = limit == 0 ? 0 : ((int) (total / (float) limit) + 1);
        int page = (limit == 0 ? 0 : offset / limit) + 1;
        
        List<T> sublist = resources.subList(offset, Math.min(offset + limit, resources.size()));
        int shown = sublist.size();
        
        //resource and page states
        result.put("total", total);
        result.put("shown", shown);
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("page", page);
        
        //String limitStr = limit != defaultLimit ? ("&limit=" + limit) : "";
        
        Integer limitValue = limit != defaultLimit ? limit : null;
        
        JSONArray pageArray = new JSONArray();
        int startPage = Math.max(1, page - 5);
        //int endPage = Math.min(pages, page + 5);
        for(int i = startPage; i <=  Math.min(pages, startPage+10); i++) {
            JSONObject obj = new JSONObject();
            
            obj.put("active", i == page);
            obj.put("number", i);
            //obj.put("path", req.uri() + "?offset=" + (limit*(i-1)) + limitStr);
            obj.put("path", createQueryParameterUri(req, "offset", (limit*(i-1)), "limit", limitValue));
            
            pageArray.put(obj);
        }
        result.put("pages", pageArray);
        
        
        
        //page links
        result.put("first", req.uri());
        if(offset + limit < total) {
            //result.put("next", req.uri() + "?offset=" + (offset + limit) + limitStr);
            result.put("next", createQueryParameterUri(req, "offset", (offset + limit), "limit", limitValue));
        }
        if(offset > 0 && offset - limit >= 0) {
            //if(offset - limit != 0) {
            //    result.put("prev", req.uri());
            //} else {
                //result.put("prev", req.uri() + "?offset=" + (offset - limit) + limitStr);
                result.put("prev", createQueryParameterUri(req, "offset", (offset - limit), "limit", limitValue));
            //}
        }
        if(total > limit) {
            //result.put("last", req.uri() + "?offset=" + (total - limit) + limitStr);
            result.put("last", createQueryParameterUri(req, "offset", (total - limit), "limit", limitValue));
        }
        
        return sublist;
    }
    
    protected String createQueryParameterUri(Request req, Object... keyValueParams) {
        Map<String, String[]> qpm = req.queryMap().toMap();
        
        for(int i = 0; i < keyValueParams.length; i += 2) {
            if(keyValueParams[i+1] == null) {
                qpm.remove((String) keyValueParams[i]);
            } else {
                qpm.put((String) keyValueParams[i], new String[] { String.valueOf(keyValueParams[i+1]) });
            }
        }
        
        String result = req.uri();
        if(!qpm.isEmpty()) {
            result += "?";
            
            StringJoiner sj = new StringJoiner("&");
            
            for(Entry<String, String[]> e : qpm.entrySet()) {
                String segment = "";
                segment += e.getKey();
                if(e.getValue().length > 0) {
                    segment += "=";
                    segment += e.getValue()[0];
                }
                sj.add(segment);
            }
            
            result += sj.toString();
        }
        return result;
    }
    
    protected Object defaultPost(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction, Resource type, String counterName, boolean postUsesAutoincIds) {
        
        JSONObject json = new JSONObject(req.body());
        
        String uri;
        String path;
        if(postUsesAutoincIds) {
            int id = counter.getIncreased(counterName);
            path = req.uri() + "/" + id;
            uri = serverAddress + path;
        } else {
            if(!json.has("id")) {
                Spark.halt(HttpStatus.BAD_REQUEST_400, "id not found");
            }
            path = req.uri() + "/" + json.getString("id");
            uri = serverAddress + path;
            json.remove("id");
        }
        
        Resource res = ResourceFactory.createResource(uri);

        Model model = converter.toModel(res.getURI(), json);

        //to have at least one triple about it
        model.add(res, RDF.type, type);

        RDFGraphStorage storage = graphFunction.apply(req);
        storage.executeWrite(() -> {
            storage.getModel().add(model);
        });

        resp.header(HttpHeader.LOCATION.asString(), path);
        resp.status(HttpStatus.CREATED_201);

        return "";
    }

    protected Object defaultPut(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction) {
        Resource res = resource(req);

        RDFGraphStorage storage = graphFunction.apply(req);
        
        if(!storage.exists(res)) {
            Spark.halt(HttpStatus.NOT_FOUND_404, "Resource not found");
        }
        
        JSONObject json = new JSONObject(req.body());
        
        if(json.isEmpty()) {
            Spark.halt(HttpStatus.BAD_REQUEST_400, "Empty body not allowed");
        }

        Model model = converter.toModel(res.getURI(), json);

        //model.write(System.out, "TTL");

        storage.executeWrite(() -> {
            //because of put remove outgoing edges first
            storage.getModel().removeAll(res, null, null);
            //add given data
            storage.getModel().add(model);
        });

        return "";
    }

    protected Object defaultPatch(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction) {
        Resource res = resource(req);

        JSONObject json = new JSONObject(req.body());

        //TODO maybe allow special properties "add.*" and "remove.*" like "add.hasVersion": ["http://.../gameversion/1"]
        //works only for object properties
        
        Model model = converter.toModel(res.getURI(), json);

        //collect to delete properties once
        Map<Property, List<Statement>> p2stmts = new HashMap<>();
        for (Statement stmt : model.listStatements().toList()) {
            List<Statement> l = p2stmts.computeIfAbsent(stmt.getPredicate(), p -> new ArrayList());
            l.add(stmt);
        }

        //because PATCH { "p": [] } means remove all outgoing "p" properties  
        List<Property> emptyProperties = converter.getEmptyArrayProperties(json);

        RDFGraphStorage storage = graphFunction.apply(req);
        
        storage.executeWrite(() -> {
            //because of patch we remove all properties and add them new
            for (Entry<Property, List<Statement>> e : p2stmts.entrySet()) {
                //remove outgoing
                storage.getModel().removeAll(res, e.getKey(), null);
                //but add the new ones
                storage.getModel().add(e.getValue());
            }

            //because PATCH { "p": [] } means remove all outgoing "p" properties  
            for (Property p : emptyProperties) {
                //remove outgoing
                storage.getModel().removeAll(res, p, null);
            }
        });

        return "";
    }
    
    protected Object defaultPatchBulk(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction) {
        JSONObject json = new JSONObject(req.body());

        //TODO maybe allow special properties "add.*" and "remove.*" like "add.hasVersion": ["http://.../gameversion/1"]
        //works only for object properties
        
        Map<Resource, Map<Property, List<Statement>>> r2p2stmts = new HashMap<>();
        Map<Resource, List<Property>> r2emptyProps = new HashMap<>();
        
        for(String path : json.keySet()) {
        
            String uri = this.serverAddress + path;
            Resource res = ResourceFactory.createResource(uri);
            
            Model model = converter.toModel(uri, json.getJSONObject(path));

            //collect to delete properties once
            Map<Property, List<Statement>> p2stmts = new HashMap<>();
            for (Statement stmt : model.listStatements().toList()) {
                List<Statement> l = p2stmts.computeIfAbsent(stmt.getPredicate(), p -> new ArrayList());
                l.add(stmt);
            }

            //because PATCH { "p": [] } means remove all outgoing "p" properties  
            List<Property> emptyProperties = converter.getEmptyArrayProperties(json);
            
            r2p2stmts.put(res, p2stmts);
            r2emptyProps.put(res, emptyProperties);
        }
        
        //once written
        RDFGraphStorage storage = graphFunction.apply(req);
        storage.executeWrite(() -> {
            //because of patch we remove all properties and add them new
            for (Entry<Resource, Map<Property, List<Statement>>> e : r2p2stmts.entrySet()) {
                for (Entry<Property, List<Statement>> e2 : e.getValue().entrySet()) {
                    //remove outgoing
                    storage.getModel().removeAll(e.getKey(), e2.getKey(), null);
                    //but add the new ones
                    storage.getModel().add(e2.getValue());
                }
            }

            //because PATCH { "p": [] } means remove all outgoing "p" properties  
            for (Entry<Resource, List<Property>> e : r2emptyProps.entrySet()) {
                for (Property p : e.getValue()) {
                    //remove outgoing
                    storage.getModel().removeAll(e.getKey(), p, null);
                }
            }
        });

        return "";
    }

    protected Object defaultDelete(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction) {
        Resource res = resource(req);
        
        RDFGraphStorage storage = graphFunction.apply(req);
        
        storage.executeWrite(() -> {
            //all incoming and outgoing links
            storage.getModel().removeAll(res, null, null);
            storage.getModel().removeAll(null, null, res);
        });
        return "";
    }
    
    protected Object defaultDeleteAll(Request req, Response resp, Function<Request, RDFGraphStorage> graphFunction, Resource type) {
        RDFGraphStorage storage = graphFunction.apply(req);
        
        List<Resource> toBeDeleted = storage.calculateRead(() -> {
            return storage.getModel().listSubjectsWithProperty(RDF.type, type).toList();
        });
        
        storage.executeWrite(() -> {
            for(Resource res : toBeDeleted) {
                //all incoming and outgoing links
                storage.getModel().removeAll(res, null, null);
                storage.getModel().removeAll(null, null, res);
            }
        });
        
        return "";
    }

    protected Object response(Request req, Response resp,
            Function<RenderModel, String> renderFunction,
            Consumer<JSONObject> jsonConsumer,
            Consumer<Model> rdfConsumer) {

        String accept = req.headers(HttpHeader.ACCEPT.asString());
        if(accept == null) {
            accept = MimeTypes.Type.APPLICATION_JSON.asString();
        }

        List<String> supported = new ArrayList<>();

        if (rdfConsumer != null) {
            supported.add("text/turtle");
        }
        if (jsonConsumer != null) {
            supported.add("application/json");
        }
        if (renderFunction != null) {
            supported.add("text/html");
        }

        String match = MIMEParse.bestMatch(supported, accept);

        Object result = null;
        switch (match) {
            case "text/html": {
                resp.type("text/html");
                RenderModel model = getDefaultModel(req);
                String templateName = renderFunction.apply(model);
                result = render(model, templateName);
                break;
            }
            case "application/json": {
                resp.type("application/json");
                JSONObject json = new JSONObject();
                JSONUtils.forceLinkedHashMap(json);
                jsonConsumer.accept(json);
                result = json.toString(2);
                break;
            }
            case "text/turtle": {
                resp.type("text/turtle");
                Model model = ModelFactory.createDefaultModel();
                rdfConsumer.accept(model);
                StringWriter sw = new StringWriter();
                model.write(sw, "TTL");
                result = sw.toString();
                break;
            }
            case "": {
                resp.status(HttpStatus.NOT_ACCEPTABLE_406);
                result = "";
                break;
            }
        }
        return result;
    }
    
    protected String render(RenderModel m, String templateName) {
        return freeMarkerEngine.render(new ModelAndView(m, templateName));
    }

    protected List<QuerySolution> query(ParameterizedSparqlString pss) {
        return Txn.calculateRead(datasetStorage.getDataset(), () -> {
        
            Query query = pss.asQuery();
            
            QueryExecution qe = QueryExecutionFactory.create(query, datasetStorage.getDataset());
            
            List<QuerySolution> qss = ResultSetFormatter.toList(qe.execSelect());

            if (DEBUG_SPARQL) {
                System.out.println(query.toString().trim());
                System.out.println(qss.size() + " results");
                System.out.println();
            }

            qe.close();
            return qss;
        });
    }
    protected <T> T query(ParameterizedSparqlString pss, Function<ResultSet, T> f) {
        return Txn.calculateRead(datasetStorage.getDataset(), () -> {
        
            Query query = pss.asQuery();
            
            QueryExecution qe = QueryExecutionFactory.create(query, datasetStorage.getDataset());
            
            
            T t = f.apply(qe.execSelect());

            if (DEBUG_SPARQL) {
                System.out.println(query.toString().trim());
                System.out.println();
            }

            qe.close();
            return t;
        });
    }
    
    protected ParameterizedSparqlString getQuery(String sparqlResource) {
        ParameterizedSparqlString pss;
        try {
            String prefix = "";
            //dynamic prefixes
            prefix += "\nPREFIX "+ ldaf.getSettings().getPrefix() +": <" + ldaf.getSettings().getOntologyNamespace() + ">";
            for(Entry<String, String> e : PrefixMapping.Standard.getNsPrefixMap().entrySet()) {
                prefix += "\nPREFIX "+ e.getKey() +": <" + e.getValue() + ">";
            }
            pss = new ParameterizedSparqlString(prefix + IOUtils.toString(LinkedDataResource.class.getResourceAsStream(SPARQL_PATH + "/" + sparqlResource), "UTF-8"));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return pss;
    }

    protected RenderModel getDefaultModel(Request req) {
        RenderModel m = new RenderModel();
        m.put("selfhref", address(req.uri()));
        m.put("selfpath", req.uri());
        m.put("isHome", req.uri().equals("/"));
        m.put("isInfo", req.uri().startsWith("/info"));
        m.put("sitename", ldaf.getSettings().getSitename());
        
        //add query params too
        for(String queryParam : req.queryParams()) {
            m.put(QUERY_PARAM_PREFIX + queryParam, req.queryParams(queryParam));
        }

        //to get logged in user info
        try {
            User user = userService.get(authController.getUserNameFromToken(req));
            m.put("user", user);
        } catch(Exception e) {
            //ignore
        }

        return m;
    }

    protected String address(Request req) {
        return serverAddress + req.uri();
    }

    protected String address(String path) {
        return serverAddress + path;
    }

    protected Resource resource(Request req) {
        return ResourceFactory.createResource(serverAddress + req.uri());
    }

    protected Model modelAbout(Resource resource, RDFGraphStorage storage) {
        Model m = storage.calculateRead(() -> {
            boolean contains = storage.getModel().containsResource(resource);
            if (!contains) {
                return null;
            }

            //get model about subject
            return storage.getModel().query(new SimpleSelector(resource, null, (RDFNode) null));
        });
        return m;
    }
    
    protected Model modelAbout(Resource resource, Model model) {
        boolean contains = model.containsResource(resource);
        if (!contains) {
            return null;
        }

        return ModelFactory.createUnion(
                model.query(new SimpleSelector(resource, null, (RDFNode) null)),
                model.query(new SimpleSelector(null, null, resource))
        );
    }

    protected void copy(JSONObject src, JSONObject trg) {
        for (String key : src.keySet()) {
            trg.put(key, src.get(key));
        }
    }

    protected String getUserGraphUri(Request req) {
        String username = authController.getUserNameFromToken(req);
        return serverAddress + "/user/" + username;
    }

    protected String getUserGraphUri(String username) {
        return serverAddress + "/user/" + username;
    }

    /**
     * Removes the last path segment of the uri.
     *
     * @param path
     * @return
     */
    protected String up(String path) {
        return path.substring(0, path.lastIndexOf("/"));
    }

    protected String ontologyAddress(String localname) {
        return ldaf.getSettings().getOntologyNamespace() + localname;
    }
    
    protected Resource ontologyResource(String localname) {
        return ResourceFactory.createResource(ontologyAddress(localname));
    }
    
    protected Property ontologyProperty(String localname) {
        return ResourceFactory.createProperty(ontologyAddress(localname));
    }

}