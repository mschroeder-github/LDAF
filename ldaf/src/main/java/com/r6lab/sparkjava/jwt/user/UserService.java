package com.r6lab.sparkjava.jwt.user;

import java.util.ArrayList;
import java.util.List;
import de.dfki.sds.ldaf.LinkedDataApplicationSettings;
import de.dfki.sds.ldaf.rdf.RDFDatasetStorage;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public final class UserService {

    private RDFDatasetStorage datasetStorage;
    private LinkedDataApplicationSettings settings;
    
    public UserService(RDFDatasetStorage datasetStorage, LinkedDataApplicationSettings settings) {
        this.datasetStorage = datasetStorage;
        this.settings = settings;
    }
    
    public final void register(String userName, String password, String passwordHash, String firstName, String lastName, String secret) {
        if(firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is empty.");
        }
        if(lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is empty.");
        }
        
        if(!userName.matches("\\w+")) {
            throw new IllegalArgumentException("Username has to match [a-zA-Z_0-9]+ (no spaces or symbols).");
        }
        
        String userUri = settings.getUserNamespace() + userName;
        
        if(datasetStorage.containsGraph(userUri)) {
            throw new IllegalArgumentException("User with user name '"+ userName +"' already exists.");
        }
        
        if(password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is empty.");
        }
        
        if(settings.hasRegisterSecret() && !settings.getRegisterSecret().equals(secret)) {
            throw new IllegalArgumentException("Registration secret is not correct.");
        }
        
        writeUserToGraph(userUri, User.of(userName, passwordHash, firstName, lastName, new ArrayList<>(), ""));
    }

    public final User get(String userName) {
        String userUri = settings.getUserNamespace() + userName;
        
        if(!datasetStorage.containsGraph(userUri)) {
            throw new IllegalArgumentException("User does not exist");
        }
        
        RDFGraphStorage graph = datasetStorage.getGraph(userUri);
        
        User user = graph.calculateRead(() -> {
            Resource userResource = ResourceFactory.createResource(userUri);
            
            String password = graph.getModel().listObjectsOfProperty(userResource, settings.getPasswordProperty()).next().asLiteral().getLexicalForm();
            
            String firstName = graph.getModel().listObjectsOfProperty(userResource, settings.getFirstNameProperty()).next().asLiteral().getLexicalForm();
            String lastName = graph.getModel().listObjectsOfProperty(userResource, settings.getLastNameProperty()).next().asLiteral().getLexicalForm();
            
            //TODO
            String logo = "";
            //NodeIterator logoIter = graph.getModel().listObjectsOfProperty(userResource, settings.getLogoProperty());
            //if(logoIter.hasNext()) {
            //    logo = logoIter.next().asResource().getURI();
            //}
            
            if(logo.startsWith(getSettings().getDefaultHost())) {
                logo = logo.substring(getSettings().getDefaultHost().length());
            }
            
            List<Role> roles = new ArrayList<>();
            for(RDFNode roleNode : graph.getModel().listObjectsOfProperty(userResource, settings.getRoleProperty()).toList()) {
                roles.add(Role.valueOf(roleNode.asLiteral().getLexicalForm()));
            }
            
            return User.of(userName,password, firstName,lastName, roles, logo);
        });
        
        return user;
    }

    public final boolean exists(String userName) {
        String userUri = settings.getUserNamespace() + userName;
        
        if(!datasetStorage.containsGraph(userUri)) {
            return false;
        }
        return true;
    }
    
    public final void update(User u) {
        String userUri = settings.getUserNamespace() + u.getUserName();
        
        //remove everything about the user
        RDFGraphStorage graph = datasetStorage.getGraph(userUri);
        graph.executeWrite(() -> {
            Resource user = ResourceFactory.createResource(userUri);
            graph.getModel().removeAll(user, null, null);
        });
        
        //write new information
        writeUserToGraph(userUri, u);
    }
    
    private void writeUserToGraph(String userUri, User u) {
        RDFGraphStorage graph = datasetStorage.getGraph(userUri);
        
        graph.executeWrite(() -> {
            Resource user = ResourceFactory.createResource(userUri);
            
            graph.getModel().removeAll(null, null, null);
            
            graph.getModel().add(user, RDF.type, settings.getUserClass());
            graph.getModel().add(user, RDFS.label, u.getUserName());
            graph.getModel().add(user, settings.getPasswordProperty(), u.getPassword());
            graph.getModel().add(user, settings.getFirstNameProperty(), u.getFirstName() != null ? u.getFirstName() : "");
            graph.getModel().add(user, settings.getLastNameProperty(), u.getLastName() != null ? u.getLastName() : "");
            
            if(!u.getLogo().isEmpty()) {
                graph.getModel().add(user, settings.getLogoProperty(), ResourceFactory.createResource(u.getLogo()));
            }
            
            for(Role r : u.getRoles()) {
                graph.getModel().add(user, settings.getRoleProperty(), r.name());
            }
        });
    }

    public LinkedDataApplicationSettings getSettings() {
        return settings;
    }
    
}
