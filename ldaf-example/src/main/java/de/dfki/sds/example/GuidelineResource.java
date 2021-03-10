
package de.dfki.sds.example;

import de.dfki.sds.ldaf.LinkedDataResource;

/**
 * A linked data resource to manage guidelines.
 */
public class GuidelineResource extends LinkedDataResource {

    @Override
    public void init() {
        this.defaultRoutes("guideline", req -> getUserGraph(req), ontologyResource("Guideline"));
    }
    
}
