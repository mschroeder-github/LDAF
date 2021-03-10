package de.dfki.sds.ldaf.rdf;

import java.util.function.Supplier;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 * An RDF graph storage for knowledge graph and other graph-like RDF data stores.
 */
public abstract class RDFGraphStorage  {
    
    private String id;
    
    public RDFGraphStorage(String id) {
        this.id = id;
    }
    
    public abstract Model getModel();
    
    
    public abstract void executeRead(Runnable r);
    
    public abstract void executeWrite(Runnable r);
    
    public abstract <T> T calculateRead(Supplier<T> s);
    
    public abstract <T> T calculateWrite(Supplier<T> s);
    
    public abstract boolean exists(Resource res);
    
    //every graph can have a name and a prefix
    //the id is the graph's URI
    
    public abstract void setLabel(String label);
    
    public abstract String getLabel();
    
    public abstract void setPrefix(String prefix);
    
    public abstract String getPrefix();

    public String getId() {
        return id;
    }
    
    public String getURI() {
        return id;
    }
    
}
