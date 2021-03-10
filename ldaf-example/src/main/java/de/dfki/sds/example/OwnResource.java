
package de.dfki.sds.example;

import de.dfki.sds.ldaf.LinkedDataResource;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.XSD;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * 
 */
public class OwnResource extends LinkedDataResource {

    @Override
    public void init() {
        Spark.get("/own", this::ownRoute);
        Spark.get("/another", this::anotherRoute);
    }
    
    private Object ownRoute(Request req, Response resp) {
        JSONObject obj = new JSONObject();
        obj.put("Var", 5);
        
        return response(req, resp, 
                renderModel -> {
                    prepareRenderModel(renderModel, obj, req);
                    return "own.html";
                },
                json -> {
                    json.put("Var", 5);
                    copy(obj, json);
                }, 
                model -> {
                    Property p = ResourceFactory.createProperty(ontologyAddress("var"));
                    model.addLiteral(resource(req), p, 5);
                }
        );
    }
    
    private Object anotherRoute(Request req, Response resp) {
        RDFGraphStorage graph = datasetStorage.getGraph("graph:uri");
        RDFGraphStorage userGraph = getUserGraph(req);
        
        Model subgraph =
        userGraph.calculateRead(() -> {
            QueryExecution qe = QueryExecutionFactory.create(
                    "PREFIX ex: <"+ ldaf.getSettings().getOntologyNamespace() +">\n" + 
                    "PREFIX xsd: <"+ XSD.NS +">\n" + 
                    "CONSTRUCT { ?gl ?p ?o } WHERE { ?gl a ex:Guideline ; ex:validFrom ?validFrom ; ?p ?o . FILTER(xsd:date(?validFrom) < xsd:date(NOW())) }",
                    userGraph.getModel()
            );
            return ModelFactory.createDefaultModel().add(qe.execConstruct());
        });
        
        JSONArray guidelines = converter.toJSON(subgraph);
        JSONObject result = new JSONObject();
        result.put("guidelines", guidelines);
        
        return response(req, resp, 
                renderModel -> {
                    prepareRenderModel(renderModel, result, req);
                    return "another.html";
                },
                json -> {
                    copy(result, json);
                }, 
                model -> {
                    model.add(subgraph);
                }
        );
    }
    
}
