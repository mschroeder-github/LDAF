package de.dfki.sds.ldaf.resources;

import de.dfki.sds.ldaf.LinkedDataResource;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.system.Txn;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 *
 * @author Markus Schr&ouml;der
 */
public class Sparql extends LinkedDataResource {

    @Override
    public void init() {
        Spark.get("/sparql", this::getSparql);
        Spark.post("/sparql", this::postSparql);
    }
    
    private Object getSparql(Request req, Response resp) {
        return response(req, resp, model -> "sparql.html", null, null);
    }
    
    private Object postSparql(Request req, Response resp) {
        String query = req.queryParamOrDefault("query", "");
        
        Model union = getUnionOfVisibleModels(req);
        QueryExecution qe = QueryExecutionFactory.create(query, union);
        ResultSet rs = qe.execSelect();
        
        //run and convert to json
        JSONObject result = Txn.calculateRead(datasetStorage.getDataset(), () -> {
            StringWriter sw = new StringWriter();
            OutputStream out = new WriterOutputStream(sw, StandardCharsets.UTF_8);
            ResultSetFormatter.outputAsJSON(out, rs);
            return new JSONObject(sw.toString());
        });
        
        //return json
        return response(req, resp, null, json -> {
            copy(result, json);
        }, null);
    }
    
}
