package de.dfki.sds.ldaf;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Converts between JSON and RDF or JSON and RenderModel.
 */
public class Converter {

    public static final String INCOMING = "_incoming";
    public static final String LIST_POSTFIX = ":list";

    private String serverAddress;
    private String ontologyNamespace;

    public Converter(String serverAddress, String ontologyNamespace) {
        this.serverAddress = serverAddress;
        this.ontologyNamespace = ontologyNamespace;
    }
    
    private Comparator<Statement> statementComp = (a, b) -> {
        if (a.getObject().isLiteral() && b.getObject().isResource()) {
            return -1;

        } else if (a.getObject().isResource() && b.getObject().isLiteral()) {
            return 1;

        } else if (a.getObject().isLiteral() && b.getObject().isLiteral()
                || a.getObject().isResource() && b.getObject().isResource()) {

            return a.getPredicate().getLocalName().compareTo(b.getPredicate().getLocalName());
        }

        return 0;
    };

    public JSONObject toJSON(Resource subject, Model model) {
        return toJSON(subject, model, 1);
    }

    public JSONObject toJSON(Resource subject, Model model, int depth) {
        JSONObject obj = new JSONObject();
        JSONUtils.forceLinkedHashMap(obj);
        
        if(subject.isAnon() && subject.canAs(RDFList.class)) {
            RDFList rdflist = model.getList(subject);
            
            obj.put("uri", serverAddress + "/list/" + subject.getId());
            obj.put("path", "/list/" + subject.getId());
            obj.put("localname", "" + subject.getId());
            
            JSONArray arrayList = new JSONArray();
            for(RDFNode entry : rdflist.asJavaList()) {
                if(entry.isResource()) {
                    arrayList.put(toJSON(entry.asResource(), model, 0));
                }
            }
            obj.put("rdflist", arrayList);
            
            return obj;
        }

        obj.put("uri", subject.getURI());
        obj.put("path", toPath(subject.getURI()));
        String ln = getLocalName(subject);
        if(ln != null) {
            obj.put("localname", ln);
        }
        
        List<Statement> stmtList = model.listStatements(subject, null, (RDFNode) null).toList();
        stmtList.sort(statementComp);

        //put comment in front if exists
        for (Statement stmt : stmtList.toArray(new Statement[0])) {
            if (stmt.getPredicate().getLocalName().equals("comment")) {
                stmtList.remove(stmt);
                stmtList.add(0, stmt);
            }
        }
        //put label in front if exists
        for (Statement stmt : stmtList.toArray(new Statement[0])) {
            if (stmt.getPredicate().getLocalName().equals("label")) {
                stmtList.remove(stmt);
                stmtList.add(0, stmt);
            }
        }

        for (Statement stmt : stmtList) {

            Property p = stmt.getPredicate();
            String pName = getLocalName(p);

            RDFNode o = stmt.getObject();
            o = model.getRDFNode(o.asNode());
            
            if(isList(o)) {
                RDFList rdfList = o.as(RDFList.class);
                rdfList.iterator().forEachRemaining(listEntry -> processObject(listEntry, obj, pName, model, depth));
            } else {
                processObject(o, obj, pName, model, depth);
            }
            
        }

        //only add incoming if resources should be opened
        if (depth > 0) {
            List<Statement> incomingList = model.listStatements(null, null, subject).toList();
            incomingList.sort(statementComp);

            JSONObject incomingObj = new JSONObject();
            JSONUtils.forceLinkedHashMap(incomingObj);

            for (Statement stmt : incomingList) {
                Property p = stmt.getPredicate();

                //incoming type is too much
                if (p.equals(RDF.type)) {
                    continue;
                }
                
                String pName = getLocalName(p);

                ensureArray(incomingObj, pName, toJSON(stmt.getSubject(), model, 0));
            }

            obj.put(INCOMING, incomingObj);
        }

        return obj;
    }

    public JSONArray toJSON(Model model) {
        JSONArray array = new JSONArray();
        for (Resource res : model.listSubjects().toList()) {
            array.put(toJSON(res, model));
        }
        return array;
    }

    private boolean isList(RDFNode node) {
        return node.isAnon() && node.canAs(RDFList.class);
    }
    
    private void processObject(RDFNode o, JSONObject obj, String pName, Model model, int depth) {
        if (o.isResource()) {
                //always array

            if (depth > 0) {
                //show more of the resource
                ensureArray(obj, pName, toJSON(o.asResource(), model, depth-1));

            } else {
                //one more hop to get label/comment
                ensureArray(obj, pName, toJSONLabelComment(o.asResource(), model));
            }
        } else if (o.isLiteral()) {
            if (obj.has(pName)) {
                throw new RuntimeException(pName + " literal property exists already");
            }
            obj.put(pName, o.asLiteral().getValue());
        }
    }
    
    private JSONObject toJSONLabelComment(Resource subject, Model model) {
        JSONObject obj = new JSONObject();
        JSONUtils.forceLinkedHashMap(obj);

        obj.put("uri", subject.getURI());
        obj.put("path", toPath(subject.getURI()));
        String ln = getLocalName(subject);
        if(ln != null) {
            obj.put("localname", ln);
        }

        NodeIterator labels = model.listObjectsOfProperty(subject, RDFS.label);
        if (labels.hasNext()) {
            obj.put("label", labels.next().asLiteral().getLexicalForm());
        }

        NodeIterator comments = model.listObjectsOfProperty(subject, RDFS.comment);
        if (comments.hasNext()) {
            obj.put("comment", comments.next().asLiteral().getLexicalForm());
        }

        return obj;
    }

    private void ensureArray(JSONObject obj, String key, Object value) {
        if (obj.has(key)) {
            Object cur = obj.get(key);
            ((JSONArray) cur).put(value);
        } else {
            JSONArray array = new JSONArray();
            array.put(value);
            obj.put(key, array);
        }
    }

    private void putOrArray(JSONObject obj, String key, Object value) {
        if (obj.has(key)) {
            Object old = obj.get(key);
            if (old instanceof JSONArray) {
                ((JSONArray) old).put(value);
            } else {
                JSONArray array = new JSONArray();
                array.put(old);
                array.put(value);
                obj.put(key, array);
            }
        } else {
            obj.put(key, value);
        }
    }

    public Model toModel(String subjectURI, JSONObject json) {
        Resource res = ResourceFactory.createResource(subjectURI);
        Model model = ModelFactory.createDefaultModel();

        for (String key : json.keySet()) {

            Property p = toProperty(key);

            //rdf stuff
            if (p == null) {
                continue;
            }

            Object value = json.get(key);

            if (value instanceof JSONArray) {
                //this is a reference to another object

                List<RDFNode> rdfListEntries = null;
                if(key.endsWith(LIST_POSTFIX)) {
                    rdfListEntries = new ArrayList<>();
                }
                
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    Object elem = array.get(i);

                    //two possibilities
                    if (elem instanceof JSONObject) {
                        //the object with uri field
                        JSONObject obj = (JSONObject) elem;
                        
                        Resource resObj = ResourceFactory.createResource(obj.getString("uri"));
                        if(rdfListEntries != null) {
                            if(!rdfListEntries.contains(resObj))
                                rdfListEntries.add(resObj);
                        } else {
                            model.add(res, p, resObj);
                        }
                    } else if (elem instanceof String) {
                        String uri = ((String) elem).trim();
                        
                        //if "" is given
                        if(uri.isEmpty()) {
                            //do not create anything
                            continue;
                        }
                        
                        //expand if it is a path
                        if(uri.startsWith("/")) {
                            uri = serverAddress + uri;
                        }
                        
                        Resource resObj = ResourceFactory.createResource(uri);
                        //just the uri directly
                        if(rdfListEntries != null) {
                            if(!rdfListEntries.contains(resObj))
                                rdfListEntries.add(resObj);
                        } else {
                            model.add(res, p, resObj);
                        }
                    }
                }
                
                if(rdfListEntries != null && !rdfListEntries.isEmpty()) {
                    RDFList list = model.createList(rdfListEntries.iterator());
                    model.add(res, p, list);
                }

            } else {
                //this is a literal
                if (value instanceof Integer) {
                    model.addLiteral(res, p, (int) value);
                } else if (value instanceof Long) {
                    model.addLiteral(res, p, (long) value);
                } else if (value instanceof Float) {
                    model.addLiteral(res, p, (float) value);
                } else if (value instanceof Double) {
                    model.addLiteral(res, p, (double) value);
                } else if (value instanceof Boolean) {
                    model.addLiteral(res, p, (boolean) value);
                } else if (value instanceof String) {
                    model.add(res, p, (String) value);
                }
            }
        }

        return model;
    }

    public String toPath(String uri) {
        if(uri == null)
            return "/";
        
        //if uri is not a http uri
        //if(!uri.startsWith("http")) {
        //    return "/" + uri;
        //}
        
        try {
            URI u = new URI(uri);
            return u.getRawPath();
        } catch (URISyntaxException ex) {
            //throw new RuntimeException(ex);
            return "/";
        }
    }
    
    //because PATCH { "p": [] } means remove all outgoing "p" properties  
    public List<Property> getEmptyArrayProperties(JSONObject json) {
        List<Property> l = new ArrayList<>();
        for (String key : json.keySet()) {
            Property p = toProperty(key);

            Object value = json.get(key);
            if (value instanceof JSONArray) {
                if (((JSONArray) value).isEmpty()) {
                    l.add(p);
                } else {
                    JSONArray array = (JSONArray) value;
                    boolean allEmpty = true;
                    for(int i = 0; i < array.length(); i++) {
                        Object entry = array.get(i);
                        if(entry instanceof String) {
                            allEmpty &= ((String) entry).isEmpty();
                        } else if (entry instanceof JSONObject) {
                            allEmpty &= ((JSONObject) entry).isEmpty();
                        }
                    }
                    if(allEmpty) {
                        l.add(p);
                    }
                }
            } else if(value == null) {
                l.add(p);
            } else if(JSONObject.NULL.equals(value)) {
                l.add(p);
            }
        }
        return l;
    }

    public Property toProperty(String jsonKey) {
        switch (jsonKey) {
            case "uri":
            case "path":
                return null;
            case "label":
                return RDFS.label;
            case "comment":
                return RDFS.comment;
            case "type":
                return RDF.type;
        }
        if(jsonKey.endsWith(LIST_POSTFIX)) {
            jsonKey = jsonKey.substring(0, jsonKey.length() - LIST_POSTFIX.length());
        }
        Property p = ResourceFactory.createProperty(ontologyNamespace + jsonKey);
        return p;
    }
    
    public RenderModel toRenderModel(JSONObject json) {
        RenderModel m = new RenderModel();
        
        for(Entry<String, Object> e : json.toMap().entrySet()) {
            if(e.getValue() instanceof JSONObject) {
                m.put(e.getKey(), toRenderModel((JSONObject) e.getValue()));
                
            } else if(e.getValue() instanceof JSONArray) {
                m.put(e.getKey(), toRenderList((JSONArray) e.getValue()));
                
            } else {
                m.put(e.getKey(), e.getValue());
            }
        }
            
        return m;
    }
    
    public List<Object> toRenderList(JSONArray array) {
        List<Object> l = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            if(array.get(i) instanceof JSONObject) {
                l.add(toRenderModel((JSONObject) array.get(i)));
                
            } else if(array.get(i) instanceof JSONArray) {
                l.add(toRenderList((JSONArray) array.get(i)));
                
            } else {
                l.add(array.get(i));
            }
        }
        return l;
    }
    
    public static String getLocalName(Resource res) {
        String uri = res.getURI();
        
        if(uri == null)
            return null;
        
        int a = uri.lastIndexOf("/");
        int b = uri.lastIndexOf("#");
        int m = Math.max(a, b);
        if(m == -1)
            return null;
        
        return uri.substring(m+1, uri.length());
    }
}
