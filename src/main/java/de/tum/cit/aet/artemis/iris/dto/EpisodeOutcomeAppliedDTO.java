package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for the episode-keyed proactive-outcome endpoint.
 * {@code applied = true} when a canonical row for the episode exists (outcome written or first-terminal-wins
 * idempotency applied); {@code applied = false} when no row exists yet (deferred - client should back-fill).
 *
 * @param applied whether the outcome was applied (true) or deferred (false, no canonical row yet)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EpisodeOutcomeAppliedDTO(boolean applied) {
}
