package de.tum.in.www1.artemis.web.websocket.dto.metis;

/**
 * Enum that is used in DTOs sent as payload in websocket messages,
 * it is used to differentiate the trigger for a websocket message and define subsequent actions in the client components
 */
public enum CrudAction {
    CREATE, UPDATE, DELETE
}
