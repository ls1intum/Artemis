package de.tum.cit.aet.artemis.web.websocket;

import java.io.Serializable;

/**
 * POJO for managing websocket error objects. error is the expected key that the client uses to decide if the request failed.
 */
public class WebsocketError implements Serializable {

    private String error;

    public WebsocketError(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
