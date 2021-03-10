package de.dfki.sds.ldaf.rdf;

import java.util.List;
import org.apache.jena.query.Dataset;

/**
 * Manages named graphs.
 */
public abstract class RDFDatasetStorage {
    
    public abstract void addGraph(String uri);
    
    public abstract void removeGraph(String uri);
    
    public abstract boolean containsGraph(String uri);
    
    public abstract List<String> getGraphNames();
    
    public abstract Dataset getDataset();
    
    public abstract RDFGraphStorage getGraph(String uri);
    
}
