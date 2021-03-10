package de.dfki.sds.ldaf.resources;

import de.dfki.sds.ldaf.LinkedDataResource;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * The root (/) resource.
 */
public class Root extends LinkedDataResource {

    @Override
    public void init() {
        Spark.get("/", this::getRoot);
        
        //bulk patch only for user graph
        Spark.patch("/", (req, resp) -> this.defaultPatchBulk(req, resp, rq -> this.getUserGraph(rq)));
    }
    
    private Object getRoot(Request req, Response resp) {
        return response(req, resp, m -> {
            return "root.html";
        }, json -> {
            //empty
        }, model -> {
            //empty
        });
    }

}
