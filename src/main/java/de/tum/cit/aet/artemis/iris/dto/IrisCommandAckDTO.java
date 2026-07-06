package de.tum.cit.aet.artemis.iris.dto;

/**
 * The client's reply to an {@link IrisCommandRequestWebsocketDTO}: whether the command was actually carried out on the client.
 *
 * @param correlationId matches the originating request
 * @param applied       whether the client executed the command (e.g. the combined view was still open and it navigated)
 */
public record IrisCommandAckDTO(String correlationId, boolean applied) {
}
