package de.dfki.sds.ldaf;

import java.util.HashMap;
import java.util.Map;

/**
 * Used for freemarker HTML rendering.
 */
public class RenderModel extends HashMap<String, Object> {

    public RenderModel() {
    }

    public RenderModel(Map<? extends String, ? extends Object> m) {
        super(m);
    }
    
}
