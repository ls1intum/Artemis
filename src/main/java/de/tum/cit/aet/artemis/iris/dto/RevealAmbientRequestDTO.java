package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for {@code POST exercises/{exerciseId}/episodes/{episodeId}/reveal}.
 *
 * @param hintText        the client's copy of the ambient hint. Accepted so the wire format stays stable, but
 *                            deliberately never read: the reveal persists the text Artemis recorded when it offered
 *                            the hint, so a caller cannot author an assistant message
 * @param level           the intervention level tag (e.g. {@code "ambient"}, {@code "stale"}). Accepted for wire
 *                            stability, not read and not stored as a separate column
 * @param clientMessageId a client-generated UUID. Accepted for wire stability, but NOT read and NOT an
 *                            idempotency key: idempotency is scoped to (user, exercise, episode) and enforced by
 *                            the episode's recorded offer
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RevealAmbientRequestDTO(String hintText, String level, String clientMessageId) {
}
