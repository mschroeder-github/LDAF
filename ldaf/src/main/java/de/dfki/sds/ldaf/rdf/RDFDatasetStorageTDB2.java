package de.dfki.sds.ldaf.rdf;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * A TDB2 implementation of RDFDatasetStorage.
 */
public class RDFDatasetStorageTDB2 extends RDFDatasetStorage {

    private Dataset dataset;
    private String location;
    
    public RDFDatasetStorageTDB2(String location) {
        dataset = TDB2Factory.connectDataset(location);
        
        //dataset.addNamedModel(location, model)
        //dataset.containsNamedModel(location)
        //dataset.removeNamedModel(location)
    }
    
    public void clear() {
        Txn.executeWrite(dataset, () -> {
            for(String name : getGraphNames()) {
                dataset.removeNamedModel(name);
            }
        });
    }

    public void remove() {
        close();
        FileUtils.deleteQuietly(new File(location));
    }

    public long size() {
        return getGraphNames().size();
    }

    public void close() {
        dataset.close();
    }

    @Override
    public List<String> getGraphNames() {
        return Txn.calculateRead(dataset, () -> {
            List<String> result = new ArrayList<>();
            dataset.listNames().forEachRemaining(result::add);
            return result;
        });
    }

    @Override
    public void addGraph(String uri) {
        Txn.executeWrite(dataset, () -> {
            Model m = ModelFactory.createDefaultModel();
            m.add(ResourceFactory.createResource(uri), RDF.type, RDFS.Container);
            dataset.addNamedModel(uri, m);
        });
    }

    @Override
    public void removeGraph(String uri) {
        Txn.executeWrite(dataset, () -> {
            dataset.removeNamedModel(uri);
        });
    }

    @Override
    public boolean containsGraph(String uri) {
        return Txn.calculateWrite(dataset, () -> {
            return dataset.containsNamedModel(uri);
        });
    }

    @Override
    public Dataset getDataset() {
        return dataset;
    }

    @Override
    public RDFGraphStorage getGraph(String uri) {
        return new RDFGraphStorageTDB2(uri, dataset);
    }
    
}
