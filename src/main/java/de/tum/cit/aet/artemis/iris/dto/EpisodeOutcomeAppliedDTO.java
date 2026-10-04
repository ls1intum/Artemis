package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for the episode-keyed proactive-outcome endpoint.
 * {@code applied = true} when a canonical row for the episode exists (outcome written or first-terminal-wins
 * idempotency applied); {@code applied = false} when no row exists yet (deferred - client should back-fill).
 *
 * <p>
 * Uses bare {@code @JsonInclude} (defaults to ALWAYS) so that {@code "applied":false} is always present on
 * the wire. NON_EMPTY would suppress the {@code false} value and produce {@code {}}, making it impossible for
 * the client to distinguish "deferred" from a malformed response.
 *
 * @param applied whether the outcome was applied (true) or deferred (false, no canonical row yet)
 */
@JsonInclude
public record EpisodeOutcomeAppliedDTO(boolean applied) {
}
