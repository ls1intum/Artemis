package de.tum.in.www1.artemis.web.websocket.dto;

/**
 * Enum that is used in the MetisPostDTO sent as payload in websocket messages,
 * it is used to differentiate the trigger for a websocket message and define subsequent actions in the client components
 */
public enum MetisPostAction {
    CREATE_POST, UPDATE_POST, DELETE_POST
}
