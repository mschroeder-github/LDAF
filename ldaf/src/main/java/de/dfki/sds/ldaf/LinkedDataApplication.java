package de.dfki.sds.ldaf;

import com.google.gson.GsonBuilder;
import com.r6lab.sparkjava.jwt.TokenService;
import com.r6lab.sparkjava.jwt.controller.AuthController;
import com.r6lab.sparkjava.jwt.user.UserService;
import de.dfki.sds.ldaf.rdf.RDFDatasetStorage;
import de.dfki.sds.ldaf.rdf.RDFDatasetStorageTDB2;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import de.dfki.sds.ldaf.resources.Ontology;
import de.dfki.sds.ldaf.resources.Root;
import de.dfki.sds.ldaf.resources.Search;
import de.dfki.sds.ldaf.resources.Sparql;
import de.dfki.sds.ldaf.resources.Upload;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import spark.template.freemarker.FreeMarkerEngine;

/**
 * The class for building linked data applications.
 */
public class LinkedDataApplication {
 
    private static final String ROOT_PATH = "/de/dfki/sds/ldaf";
    
    private LinkedDataApplicationSettings settings;
    private String serverAddress;
    
    private FreeMarkerEngine freeMarkerEngine;
    
    private RDFDatasetStorage datasetStorage;
    
    public static final CommandLineParser parser = new DefaultParser();
    private CommandLine cmd;
    public static final Options options = new Options();
    static {
        options.addOption("h", "help", false, "prints this help");
        options.addOption("a", "serveraddr", true, "Where the server is running");
    }
    
    private Counter counter;
    
    private static final String SECRET_JWT = "Q5M7clOZkFz1XBKh3YW7";
    private TokenService tokenService;
    private AuthController authController;
    private UserService userService;
    private DataAccessPointRegistry registry;
    
    private Map<Class, Object> class2obj;
    
    private Upload upload;
    private Search search;
    
    private Converter converter;
    
    public LinkedDataApplication(String[] args, LinkedDataApplicationSettings settings) {
        this.settings = settings;
        this.class2obj = new HashMap<>();
        initCmd(args);
        initCounter();
        initConverter();
        initFreemarker();
        initRDF();
        initFuseki();
        initSpark();
        initJsonWebToken();
    }
   
    /**
     * Inits command line interface and exits if help is shown.
     *
     * @param args
     */
    private void initCmd(String[] args) {
        try {
            //parse it
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }

        //help
        if (cmd.hasOption("h")) {
            new HelpFormatter().printHelp(settings.getAppname(), options);
            System.exit(0);
        }

        serverAddress = cmd.getOptionValue("serveraddr", settings.getDefaultHost());
        
        System.out.println("serverAddress: " + serverAddress);
    }
    
    private void initCounter() {
        settings.getCounterPath().mkdirs();
        counter = new Counter(settings.getCounterPath());
    }
    
    /**
     * Inits freemarker template engine to render SPARQL results.
     */
    private void initFreemarker() {
        //template (freemarker)
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_28);
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setTemplateLoader(new ClassTemplateLoader(LinkedDataApplication.class, settings.getFreemarkerTemplateClasspath()));
        freeMarkerEngine = new FreeMarkerEngine(freemarkerConfig);
    }
    
    private void initRDF() {
        settings.getTdbPath().mkdirs();
        datasetStorage = new RDFDatasetStorageTDB2(settings.getTdbPath().getAbsolutePath());
        
        //put ontology in dataset
        if(settings.getOntologyGraph() != null) {
            RDFGraphStorage store = datasetStorage.getGraph(settings.getOntologyGraph());
            store.executeWrite(() -> {
                store.getModel().removeAll();
                store.getModel().add(settings.getOntologySupplier().get());
            });
        }
    }
    
    private void initFuseki() {
        //registry = new DataAccessPointRegistry();
    }
    
    private void initSpark() {
        System.out.println("port: " + settings.getPort());
        spark.Spark.port(settings.getPort());
        
        spark.Spark.exception(Exception.class, (exception, request, response) -> {
            exception.printStackTrace();
            response.body(exception.getMessage());
        });
        
        spark.Spark.staticFiles.location(ROOT_PATH + "/web");
        //spark.Spark.staticFiles.header("Access-Control-Allow-Origin", "*");
        
        //if ends with '/' redirect to path without '/'
        spark.Spark.before((req, res) -> {
            String path = req.pathInfo();
            if (!path.equals("/") && path.endsWith("/")) {
                res.redirect(path.substring(0, path.length() - 1));
            }
        });
        
        //spark.Spark.before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));
    }
    
    private void initJsonWebToken() {
        tokenService = new TokenService(SECRET_JWT);
        
        userService = new UserService(datasetStorage, settings);
        
        authController = new AuthController(new GsonBuilder().create(), userService, tokenService);
        //calls init
        initResource(authController);
    }
    
    private void initConverter() {
        this.converter = new Converter(serverAddress, settings.getOntologyNamespace());
    }
    
    
    
    //called from outside
    
    public <T  extends LinkedDataResource> T initResource(Class<T> resourceClass) {
        T res;
        try {
            res = resourceClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
        initResource(res);
        class2obj.put(resourceClass, res);
        return res;
    }
    
    public void initResource(LinkedDataResource res) {
        res.datasetStorage = datasetStorage;
        res.freeMarkerEngine = freeMarkerEngine;
        res.serverAddress = serverAddress;
        res.counter = counter;
        res.authController = authController;
        res.userService = userService;
        res.converter = converter;
        res.ldaf = this;
        res.init();
    }

    public <T> T getResource(Class<T> resourceClass) {
        return (T) class2obj.get(resourceClass);
    }
    
    public Upload getUploader() {
        return upload;
    }

    public Search getSearch() {
        return search;
    }
    
    public LinkedDataApplicationSettings getSettings() {
        return settings;
    }
    
    public void defaultRoot() {
        initResource(Root.class);
    }
    
    public void defaultUploader() {
        upload = initResource(Upload.class);
    }
    
    public void defaultSearch() {
        search = initResource(Search.class);
    }
    
    public void defaultOntology() {
        initResource(Ontology.class);
    }
    
    public void defaultSparql() {
        initResource(Sparql.class);
    }

    public RDFDatasetStorage getDatasetStorage() {
        return datasetStorage;
    }

}
