package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sent to the client to request execution of a command while the Iris pipeline is still running (before the answer arrives). The client tries to carry it out and replies with an
 * {@link IrisCommandAckDTO} carrying the same {@code correlationId}.
 * <p>
 * Delivery is to the user, so this reaches every tab they have the session open in, and every one of them carries the command out — the student should find the same position
 * whichever tab they look at next. {@code targetClientId} does not restrict that; it names the single tab that <em>answers</em>, so that a bystanding tab cannot report failure
 * while the tab the chat run was started from is still navigating. Addressing rather than routing: the transport stays the plain user destination that works across nodes.
 *
 * @param correlationId  opaque id correlating this request with its ack
 * @param type           command type discriminator
 * @param parameters     command-specific parameters
 * @param targetClientId the browser tab expected to answer; null means no tab was named and any of them may
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCommandRequestWebsocketDTO(String correlationId, String type, Map<String, JsonNode> parameters, String targetClientId) {

    public IrisCommandRequestWebsocketDTO {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
