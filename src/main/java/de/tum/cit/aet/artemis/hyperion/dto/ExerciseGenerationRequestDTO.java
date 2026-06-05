package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to start an agentic whole-exercise generation or adaptation run.
 *
 * @param prompt the instruction for the run: a brief describing the exercise to create from scratch, or the feedback to address when adapting an existing one. Optional and
 *                   bounded:
 *                   this free text flows into the LLM, so it is capped to keep cost and abuse in check (the analogous {@code AtlasAgentChatRequestDTO} bounds its text the same
 *                   way).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRequestDTO(@Nullable @Size(max = 8000) String prompt) {
}
