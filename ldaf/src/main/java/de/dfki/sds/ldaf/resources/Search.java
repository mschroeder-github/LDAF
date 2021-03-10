package de.dfki.sds.ldaf.resources;

import de.dfki.sds.ldaf.LinkedDataResource;
import de.dfki.sds.ldaf.RegexUtility;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.system.Txn;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * A linked data resource that provides search via SPARQL.
 */
public class Search extends LinkedDataResource {

    @Override
    public void init() {
        Spark.get("/search", this::search);
    }

    //type can be null
    public JSONObject search(String q, Resource type, Request req) {
        if(req == null)
            return new JSONObject();
        
        RDFGraphStorage userGraph = getUserGraph(req);

        ParameterizedSparqlString pss = type == null ? this.getQuery("query.sparql") : this.getQuery("queryWithType.sparql");
        pss.setLiteral("regex", RegexUtility.quote(q));
        if(type != null) {
            pss.setIri("type", type.getURI());
        }

        List<Resource> visibleGraphs = new ArrayList<>();
        for (String g : ldaf.getSettings().getPublicGraphs()) {
            visibleGraphs.add(ResourceFactory.createResource(g));
        }
        visibleGraphs.add(ResourceFactory.createResource(userGraph.getURI()));
        pss.setValues("visibleGraphs", visibleGraphs);

        List<QuerySolution> list = query(pss);

        Collections.sort(list, (a, b) -> {
            String la = a.getLiteral("label").getLexicalForm();
            String lb = b.getLiteral("label").getLexicalForm();
            return la.compareTo(lb);
        });

        JSONObject resultObj = new JSONObject();

        resultObj.put("query", q);

        JSONArray result = new JSONArray();
        resultObj.put("result", result);

        //be in read transaction to get for each resource data
        Txn.executeRead(datasetStorage.getDataset(), () -> {

            Model union = getUnionOfVisibleModels(req);

            for (QuerySolution qs : list) {
                JSONObject entry = new JSONObject();

                entry.put("graph", qs.getResource("graph").getURI());
                entry.put("uri", qs.getResource("s").getURI());
                entry.put("path", converter.toPath(qs.getResource("s").getURI()));
                entry.put("label", qs.getLiteral("label").getLexicalForm());

                JSONObject obj = converter.toJSON(qs.getResource("s"), union, 1);
                entry.put("resource", obj);

                result.put(entry);
            }
        });
        
        return resultObj;
    }

    private Object search(Request req, Response resp) {

        String q = req.queryParams("q");
        
        //empty check
        if (q == null || q.trim().isEmpty()) {
            //empty query
            return response(req, resp, renderModel -> {
                //empty
                return "search.html";
            },
            json -> {
                //empty
            },
            model -> {
                //empty
            });
        }

        //do search
        JSONObject resultObj = search(q, null, req);

        //return result
        return response(req, resp, renderModel -> {
                renderModel.putAll(converter.toRenderModel(resultObj));
                return "search.html";
            },
            json -> {
                copy(resultObj, json);
            },
            model -> {
                //TODO search in rdf mode
            });
    }

}
