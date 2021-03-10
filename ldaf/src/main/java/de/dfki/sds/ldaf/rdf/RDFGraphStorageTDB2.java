package de.dfki.sds.ldaf.rdf;

import java.util.function.Supplier;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.resultset.ResultSetMem;
import org.apache.jena.system.Txn;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDFS;

/**
 * An RDF graph that is backed up by TDB2 on disk.
 */
public class RDFGraphStorageTDB2 extends RDFGraphStorage {
    
    private Dataset dataset;
    private Model model;
    
    public RDFGraphStorageTDB2(String uri, Dataset dataset) {
        super(uri);
        this.dataset = dataset;
        model = dataset.getNamedModel(getId());
    }
    
    private Resource getGraphResource() {
        return ResourceFactory.createResource(getId());
    }
    
    @Override
    public Model getModel() {
        return model;
    }
    
    @Override
    public void executeRead(Runnable r) {
        Txn.executeRead(dataset, r);
    }

    @Override
    public void executeWrite(Runnable r) {
        Txn.executeWrite(dataset, r);
    }

    @Override
    public <T> T calculateRead(Supplier<T> s) {
        return Txn.calculateRead(dataset, s);
    }

    @Override
    public <T> T calculateWrite(Supplier<T> s) {
        return Txn.calculateWrite(dataset, s);
    }
    
    public void clear() {
        Txn.executeWrite(dataset, () -> {
            //both works
            //dataset.removeNamedModel(getGraphUri());
            model.removeAll();
        });
    }

    public void remove() {
        clear();
    }

    public ResultSet query(String query) {
        return Txn.calculateRead(dataset, () -> {
            QueryExecution qe = QueryExecutionFactory.create(query, model); //or dataset?
            return new ResultSetMem(qe.execSelect());
        });
    }

    public void execute(String query) {
        Txn.executeWrite(dataset, () -> {
            UpdateRequest updateRequest = UpdateFactory.create(query);
            UpdateProcessor processor = UpdateExecutionFactory.create(updateRequest, dataset);
            processor.execute();
        });
    }

    public void close() {
        dataset.close();
    }
    
    public long size() {
        return Txn.calculateRead(dataset, () -> {
            return model.size();
        });
    }

    @Override
    public void setLabel(String label) {
        executeWrite(() -> {
            model.removeAll(getGraphResource(), RDFS.label, null);
            model.add(getGraphResource(), RDFS.label, label);
        });
    }

    @Override
    public String getLabel() {
        return calculateRead(() -> {
            Statement stmt = model.getProperty(getGraphResource(), RDFS.label);
            if(stmt == null)
                return null;
            return stmt.getObject().asLiteral().getLexicalForm();
        });
    }

    @Override
    public void setPrefix(String prefix) {
        executeWrite(() -> {
            model.removeAll(getGraphResource(), DCAT.keyword, null);
            model.add(getGraphResource(), DCAT.keyword, prefix);
        });
    }

    @Override
    public String getPrefix() {
        return calculateRead(() -> {
            Statement stmt = model.getProperty(getGraphResource(), DCAT.keyword);
            if(stmt == null)
                return null;
            return stmt.getObject().asLiteral().getLexicalForm();
        });
    }

    @Override
    public boolean exists(Resource res) {
        return calculateRead(() -> {
            return getModel().containsResource(res);
        });
    }

}
