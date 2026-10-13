package de.tum.cit.aet.artemis.metis.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A student's answer to the open question of their comprehension check. The text may be empty or missing: when the timer of a question expires, the client sends
 * whatever the student has typed so far. An answer longer than the configured maximum is cut, never rejected.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisAnswerRequestDTO(@Nullable String answerText) {
}
