package de.tum.cit.aet.artemis.core.service.messaging;

import java.io.Serializable;
import java.time.Instant;

/**
 * Payload sent over Hazelcast to request a websocket broker reconnect on a specific node or on all nodes.
 *
 * @param targetNodeId      hazelcast member id to handle the reconnect; use {@link #TARGET_ALL_NODES} to target all
 * @param requestedBy       login of the admin who triggered the request (for logging only)
 * @param originatingNodeId hazelcast member id that published the message (to help debugging)
 * @param timestamp         time when the request was created
 */
public record WebsocketBrokerReconnectMessage(String targetNodeId, String requestedBy, String originatingNodeId, Instant timestamp) implements Serializable {

    public static final String TARGET_ALL_NODES = "all";
}
