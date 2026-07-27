package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sent to the client to request execution of a command while the Iris pipeline is still running (before the answer arrives). The client tries to carry it out and replies with an
 * {@link IrisCommandAckDTO} carrying the same {@code correlationId}.
 *
 * @param correlationId opaque id correlating this request with its ack
 * @param type          command type discriminator
 * @param parameters    command-specific parameters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCommandRequestWebsocketDTO(String correlationId, String type, Map<String, JsonNode> parameters) {

    public IrisCommandRequestWebsocketDTO {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
