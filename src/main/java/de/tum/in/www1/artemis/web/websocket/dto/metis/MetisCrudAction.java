package de.tum.in.www1.artemis.web.websocket.dto.metis;

/**
 * Enum that is used in DTOs sent as payload in websocket messages,
 * it is used to differentiate the behavior when a websocket message is received by the client components
 */
public enum MetisCrudAction {
    CREATE, UPDATE, DELETE, READ_CONVERSATION
}
