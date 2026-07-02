package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to start an agentic whole-exercise generation or adaptation run.
 *
 * @param prompt optional brief (create) or feedback (adapt); capped to bound LLM cost/abuse
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRequestDTO(@Nullable @Size(max = 8000) String prompt) {
}
