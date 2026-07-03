package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

/**
 * The client's reply to an {@link IrisCommandRequestWebsocketDTO}: whether the command was actually carried out on the client.
 *
 * @param correlationId matches the originating request
 * @param applied       whether the client executed the command (e.g. the combined view was still open and it navigated)
 * @param reason        optional short reason when not applied (e.g. "combinedViewClosed")
 */
public record IrisCommandAckDTO(String correlationId, boolean applied, @Nullable String reason) {
}
