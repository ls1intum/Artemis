package de.tum.cit.aet.artemis.communication.dto;

/**
 * Enum that is used in DTOs sent as payload in websocket messages,
 * it is used to differentiate the behavior when a websocket message is received by the client components
 */
public enum MetisCrudAction {
    CREATE, UPDATE, DELETE, NEW_MESSAGE
}
