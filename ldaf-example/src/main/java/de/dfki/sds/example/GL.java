
package de.dfki.sds.example;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Guideline Ontology.
 */
public class GL {

    public static final String NS = "http://localhost:8081/ontology/";
    
    public static final Resource Guideline = ResourceFactory.createResource(NS + "Guideline");
    public static final Resource Attachment = ResourceFactory.createResource(NS + "Attachment");
    public static final Resource Procedure = ResourceFactory.createResource(NS + "Procedure");
    
    public static final Resource Department = ResourceFactory.createResource(NS + "Department");
    public static final Resource Kind = ResourceFactory.createResource(NS + "Kind");
    public static final Resource State = ResourceFactory.createResource(NS + "State");
    public static final Resource Category = ResourceFactory.createResource(NS + "Category");
    public static final Resource SecurityNeed = ResourceFactory.createResource(NS + "SecurityNeed");
    public static final Resource Progress = ResourceFactory.createResource(NS + "Progress");
    public static final Resource MailingList = ResourceFactory.createResource(NS + "MailingList");
    
    public static final Property manages = ResourceFactory.createProperty(NS + "manages");
    public static final Property hasProgress = ResourceFactory.createProperty(NS + "hasProgress");
    public static final Property hasId = ResourceFactory.createProperty(NS + "hasId");
    public static final Property hasNumber = ResourceFactory.createProperty(NS + "hasNumber");
    public static final Property validFrom = ResourceFactory.createProperty(NS + "validFrom");
    public static final Property invalidFrom = ResourceFactory.createProperty(NS + "invalidFrom");
    public static final Property lastModifiedDate = ResourceFactory.createProperty(NS + "lastModifiedDate");
    public static final Property isRecent = ResourceFactory.createProperty(NS + "isRecent");
    public static final Property hasCategory = ResourceFactory.createProperty(NS + "hasCategory");
    public static final Property hasNote = ResourceFactory.createProperty(NS + "hasNote");
    public static final Property hasAttachment = ResourceFactory.createProperty(NS + "hasAttachment");
    public static final Property hasAbbreviation = ResourceFactory.createProperty(NS + "hasAbbreviation");
    public static final Property hasState = ResourceFactory.createProperty(NS + "hasState");
    public static final Property hasSecurityNeed = ResourceFactory.createProperty(NS + "hasSecurityNeed");
    public static final Property hasDepartment = ResourceFactory.createProperty(NS + "hasDepartment");
    public static final Property worksAt = ResourceFactory.createProperty(NS + "worksAt");
    public static final Property inMailingList = ResourceFactory.createProperty(NS + "inMailingList");
    public static final Property hasTitle = ResourceFactory.createProperty(NS + "hasTitle");
    public static final Property hasKind = ResourceFactory.createProperty(NS + "hasKind");
    public static final Property hasEditorResponsible = ResourceFactory.createProperty(NS + "hasEditorResponsible");
    public static final Property hasEditor = ResourceFactory.createProperty(NS + "hasEditor");
    public static final Property hasReviewer = ResourceFactory.createProperty(NS + "hasReviewer");
    public static final Property plannedValidFrom = ResourceFactory.createProperty(NS + "plannedValidFrom");
    public static final Property wasFormerEditor = ResourceFactory.createProperty(NS + "wasFormerEditor");
    
    
}
