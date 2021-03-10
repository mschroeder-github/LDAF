package de.dfki.sds.ldaf;

import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import spark.Request;

/**
 * Settings for a linked data application.
 */
public class LinkedDataApplicationSettings {
    
    private int port;
    private String appname;
    private String prefix;
    private String sitename;
    private String defaultHost;
    
    //for user service
    private String userNamespace;
    private Resource userClass;
    private Property passwordProperty;
    private Property firstNameProperty;
    private Property lastNameProperty;
    private Property roleProperty;
    private Property logoProperty;
    private String adminPassword;
    private String registerSecret;
    
    //for upload
    private Resource uploadClass;
    
    private String ontologyGraph;
    private String ontologyNamespace;
    private Supplier<Model> ontologySupplier;
    
    private Function<Request, RDFGraphStorage> uploadStorage;
    
    private String freemarkerTemplateClasspath;
    
    private File tdbPath;
    private File counterPath;
    private File uploadPath;
    
    private List<String> publicGraphs;

    public LinkedDataApplicationSettings() {
        port = 8080;
        appname = "linked data library";
        defaultHost = "http://localhost:" + port;
        
        tdbPath = new File("./tdb");
        counterPath = new File("./counter");
        uploadPath = new File("./upload");
        
        publicGraphs = new ArrayList<>();
    }
    
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getSitename() {
        return sitename;
    }

    public void setSitename(String sitename) {
        this.sitename = sitename;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public String getDefaultHost() {
        return defaultHost;
    }

    public void setDefaultHost(String defaultHost) {
        this.defaultHost = defaultHost;
    }

    public Resource getUserClass() {
        return userClass;
    }

    public void setUserClass(Resource userClass) {
        this.userClass = userClass;
    }

    public Property getPasswordProperty() {
        return passwordProperty;
    }

    public void setPasswordProperty(Property passwortProperty) {
        this.passwordProperty = passwortProperty;
    }

    public Property getFirstNameProperty() {
        return firstNameProperty;
    }

    public void setFirstNameProperty(Property firstNameProperty) {
        this.firstNameProperty = firstNameProperty;
    }

    public Property getLastNameProperty() {
        return lastNameProperty;
    }

    public void setLastNameProperty(Property lastNameProperty) {
        this.lastNameProperty = lastNameProperty;
    }

    public Property getRoleProperty() {
        return roleProperty;
    }

    public void setRoleProperty(Property roleProperty) {
        this.roleProperty = roleProperty;
    }

    public String getUserNamespace() {
        return userNamespace;
    }

    public void setUserNamespace(String userNamespace) {
        this.userNamespace = userNamespace;
    }

    public Property getLogoProperty() {
        return logoProperty;
    }

    public void setLogoProperty(Property logoProperty) {
        this.logoProperty = logoProperty;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getRegisterSecret() {
        return registerSecret;
    }

    public boolean hasRegisterSecret() {
        return registerSecret != null;
    }
    public void setRegisterSecret(String registerSecret) {
        this.registerSecret = registerSecret;
    }
    
    public Supplier<Model> getOntologySupplier() {
        return ontologySupplier;
    }

    public void setOntologySupplier(Supplier<Model> ontologySupplier) {
        this.ontologySupplier = ontologySupplier;
    }

    public String getOntologyNamespace() {
        return ontologyNamespace;
    }

    public void setOntologyNamespace(String ontologyNamespace) {
        this.ontologyNamespace = ontologyNamespace;
    }

    public String getOntologyGraph() {
        return ontologyGraph;
    }

    public void setOntologyGraph(String ontologyGraph) {
        this.ontologyGraph = ontologyGraph;
    }
    
    public Resource getUploadClass() {
        return uploadClass;
    }

    public void setUploadClass(Resource uploadClass) {
        this.uploadClass = uploadClass;
    }

    public String getFreemarkerTemplateClasspath() {
        return freemarkerTemplateClasspath;
    }

    public void setFreemarkerTemplateClasspath(String freemarkerTemplateClasspath) {
        this.freemarkerTemplateClasspath = freemarkerTemplateClasspath;
    }

    public File getTdbPath() {
        return tdbPath;
    }

    public void setTdbPath(File tdbPath) {
        this.tdbPath = tdbPath;
    }

    public File getCounterPath() {
        return counterPath;
    }

    public void setCounterPath(File counterPath) {
        this.counterPath = counterPath;
    }

    public File getUploadPath() {
        return uploadPath;
    }

    public void setUploadPath(File uploadPath) {
        this.uploadPath = uploadPath;
    }

    public List<String> getPublicGraphs() {
        return publicGraphs;
    }

    public void setPublicGraphs(List<String> publicGraphs) {
        this.publicGraphs = publicGraphs;
    }

    public Function<Request, RDFGraphStorage> getUploadStorage() {
        return uploadStorage;
    }

    public void setUploadStorage(Function<Request, RDFGraphStorage> uploadStorage) {
        this.uploadStorage = uploadStorage;
    }
    
}
