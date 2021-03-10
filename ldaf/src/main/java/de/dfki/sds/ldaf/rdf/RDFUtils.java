package de.dfki.sds.ldaf.rdf;

import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;

/**
 *
 */
public class RDFUtils {

    public static String toTTL(Model model) {
        StringWriter sw = new StringWriter();
        model.write(sw, "TTL");
        return sw.toString();
    }
}
