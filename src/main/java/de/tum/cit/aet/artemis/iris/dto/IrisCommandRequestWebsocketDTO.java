package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * Sent to the client to request execution of a command while the Iris pipeline is still running (before the answer arrives). The client tries to carry it out and replies with an
 * {@link IrisCommandAckDTO} carrying the same {@code correlationId}.
 * <p>
 * Delivery is user-wide. If {@code targetClientId} is set, only that browser tab acts on the command; otherwise every subscribed tab may try it.
 *
 * @param correlationId  opaque id correlating this request with its ack
 * @param type           command type discriminator
 * @param parameters     command-specific parameters
 * @param targetClientId the browser tab that should act and answer; null means any subscribed tab may
 * @param expiresAt      epoch-millisecond deadline after which the client must not start executing the command
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCommandRequestWebsocketDTO(String correlationId, String type, Map<String, JsonNode> parameters, String targetClientId, long expiresAt) {

    public IrisCommandRequestWebsocketDTO {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
