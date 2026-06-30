package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for {@code POST exercises/{exerciseId}/episodes/{episodeId}/reveal}.
 *
 * @param hintText        the previously-hidden ambient hint text to persist
 * @param level           the intervention level tag (e.g. {@code "ambient"}, {@code "stale"}) - accepted for
 *                            future use but not stored as a separate column in the current schema
 * @param clientMessageId the client-generated UUID that serves as the idempotency key for this reveal
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RevealAmbientRequestDTO(String hintText, String level, String clientMessageId) {
}
