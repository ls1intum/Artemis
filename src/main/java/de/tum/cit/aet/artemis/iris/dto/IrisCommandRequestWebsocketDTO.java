package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sent to the client to request execution of a command while the Iris pipeline is still running (before the answer arrives). The client tries to carry it out — for a point-out:
 * navigate the combined view if it is still open — and replies with an {@link IrisCommandAckDTO} carrying the same {@code correlationId}.
 *
 * @param correlationId opaque id correlating this request with its ack
 * @param type          command type discriminator (currently only "pointOut")
 * @param lectureUnitId the lecture unit to point to (point-out)
 * @param page          1-based slide page to display, or {@code null} (point-out)
 * @param timestamp     video position in seconds to seek to, or {@code null} (point-out)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IrisCommandRequestWebsocketDTO(String correlationId, String type, long lectureUnitId, @Nullable Integer page, @Nullable Double timestamp) {
}
