package de.dfki.sds.ldaf.resources;

import de.dfki.sds.ldaf.LinkedDataResource;
import de.dfki.sds.ldaf.rdf.RDFUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * A linked data resource to query the ontology.
 */
public class Ontology extends LinkedDataResource {

    @Override
    public void init() {
        Spark.get("/ontology", this::getOntology);
        Spark.get("/ontology/*", this::getOntologyResource);
    }
    
    private Object getOntology(Request req, Response resp) {
        Model model = ldaf.getSettings().getOntologySupplier().get();
        
        return response(req, resp, m -> {
            m.put("code", insertLinks(StringEscapeUtils.escapeHtml(RDFUtils.toTTL(model))));
            return "ontology.html";
        }, json -> {
            json.put("ontology", converter.toJSON(model));
        }, mod -> {
            mod.add(model);
        });
    }
    
    private Object getOntologyResource(Request req, Response resp) {
        Model model = ldaf.getSettings().getOntologySupplier().get();
        
        Resource res = resource(req);
        Model resModel = model.query(new SimpleSelector(res, null, (RDFNode) null));
        Model resModelIncoming = model.query(new SimpleSelector(null, null, res));
        resModel.add(resModelIncoming);
        resModel.setNsPrefixes(model.getNsPrefixMap());
        
        if(resModel.isEmpty()) {
            Spark.halt(HttpStatus.NOT_FOUND_404, "resource not found");
        }
        
        return response(req, resp, m -> {
            m.put("code", insertLinks(StringEscapeUtils.escapeHtml(RDFUtils.toTTL(resModel))));
            return "ontology.html";
        }, json -> {
            copy(converter.toJSON(res, model), json);
        }, mod -> {
            mod.setNsPrefixes(model.getNsPrefixMap());
            mod.add(resModel);
        });
    }
    
    private String insertLinks(String escapedHtml) {
        
        String prefix = this.ldaf.getSettings().getPrefix();
        
        //TODO maybe better: ([^ ]+)
        Pattern linkPattern = Pattern.compile(prefix + ":(\\w+)");
        
        List<Object[]> a = new ArrayList<>();
        
        Matcher m = linkPattern.matcher(escapedHtml);
        while(m.find()) {
            a.add(new Object[] { m.start(), m.end(), m.group(1) });
        }
               
        Collections.reverse(a);
        
        StringBuilder sb = new StringBuilder(escapedHtml);
        
        for(Object[] entry : a) {
            String ln = (String) entry[2];
            sb.replace((int) entry[0], (int) entry[1], "<a href=\"" + "/ontology/" + ln + "\">"+ prefix +":" + ln + "</a>");
        }
        
        return sb.toString();
    }
    
}
