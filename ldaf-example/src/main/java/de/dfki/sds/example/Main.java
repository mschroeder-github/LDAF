
package de.dfki.sds.example;

import de.dfki.sds.ldaf.LinkedDataApplication;
import de.dfki.sds.ldaf.LinkedDataApplicationSettings;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;

public class Main {
    public static void main(String[] args) throws IOException {
        LinkedDataApplicationSettings settings = new LinkedDataApplicationSettings();
        
        int port = 8081;
        if(args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        settings.setPort(port);
        settings.setAppname("ldaf-example.jar");
        settings.setPrefix("ex");
        settings.setSitename("Example");
        
        String host = "http://localhost:8081";
        if(args.length > 1) {
            host = args[1];
        }
        settings.setDefaultHost(host);
        
        String ontologyTTL = IOUtils.toString(Main.class.getResourceAsStream("/de/dfki/sds/example/vocab/gl.ttl"), "UTF-8");
        ontologyTTL = ontologyTTL.replace("http://localhost:8081/ontology", host + "/ontology");
        Model ontology = ModelFactory.createDefaultModel().read(new StringReader(ontologyTTL), null, "TTL");
        settings.setOntologySupplier(() -> ontology);
        settings.setOntologyGraph(settings.getDefaultHost() + "/ontology");
        settings.setOntologyNamespace(settings.getDefaultHost() + "/ontology/");
        
        settings.getPublicGraphs().add(settings.getDefaultHost() + "/ontology");
        settings.getPublicGraphs().add(settings.getDefaultHost() + "/ImageGraph");
        
        settings.setUserNamespace(settings.getDefaultHost() + "/user/");
        settings.setUserClass(FOAF.Person);
        settings.setPasswordProperty(ResourceFactory.createProperty(settings.getDefaultHost() + "/ontology/password"));
        settings.setFirstNameProperty(FOAF.firstName);
        settings.setLastNameProperty(FOAF.lastName);
        settings.setRoleProperty(ResourceFactory.createProperty(settings.getDefaultHost() + "/ontology/role"));
        settings.setLogoProperty(FOAF.depiction);
        settings.setAdminPassword("a-secret");
        settings.setRegisterSecret("another-secret");
        
        settings.setUploadClass(FOAF.Image);
        
        settings.setFreemarkerTemplateClasspath("/de/dfki/sds/example/tmpl");
        
        settings.setTdbPath(new File("data/tdb"));
        settings.setCounterPath(new File("data/counter"));
        settings.setUploadPath(new File("data/upload"));
        
        LinkedDataApplication lda = new LinkedDataApplication(args, settings);
        
        //example for accessing a user's graph
        //RDFGraphStorage adminGraph = lda.getDatasetStorage().getGraph("http://localhost:8081/user/admin");
        //adminGraph.executeRead(() -> {
        //    System.out.println(RDFUtils.toTTL(adminGraph.getModel()));
        //});
        
        lda.defaultRoot();
        lda.defaultOntology();
        lda.defaultSearch();
        lda.defaultSparql();
        
        settings.setUploadStorage(req -> lda.getDatasetStorage().getGraph(settings.getDefaultHost() + "/ImageGraph"));
        lda.defaultUploader();
        
        lda.initResource(GuidelineResource.class);
        lda.initResource(OwnResource.class);
    }
}
