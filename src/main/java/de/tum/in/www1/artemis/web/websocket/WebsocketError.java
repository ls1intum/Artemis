package de.tum.in.www1.artemis.web.websocket;

import java.io.Serializable;

/**
 * POJO for managing websocket error objects.
 * error is the expected key that the client uses to decide if the request failed.
 */
public class WebsocketError implements Serializable {
    protected String error;

    WebsocketError(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
