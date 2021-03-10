
package de.dfki.sds.ldaf;

import java.util.function.Consumer;
import org.json.JSONObject;

/**
 * Influence on default routes.
 * You can change the behavior of default routes with this class.
 */
public class Influence {

    //if true, no sublist is calculated in defaultGetList, all resources will be returned
    private boolean noSublistInGetList;
    
    //here you have the possibility to change the json result in the get list method
    private Consumer<JSONObject> consumeResultInGetList;

    public boolean isNoSublistInGetList() {
        return noSublistInGetList;
    }

    public void setNoSublistInGetList(boolean noSublistInGetList) {
        this.noSublistInGetList = noSublistInGetList;
    }

    public Consumer<JSONObject> getConsumeResultInGetList() {
        return consumeResultInGetList;
    }

    public void setConsumeResultInGetList(Consumer<JSONObject> consumeResultInGetList) {
        this.consumeResultInGetList = consumeResultInGetList;
    }
    
}
