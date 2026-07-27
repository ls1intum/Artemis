package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to start an agentic whole-exercise generation or adaptation run.
 *
 * @param mode                      the run intent; see {@link GenerationMode} for why the client states it rather than the server inferring it
 * @param prompt                    optional brief (generate) or feedback (adapt); capped to bound LLM cost/abuse
 * @param selectedFeedbackThreadIds optional review-comment thread ids the adapt run should address; capped to bound the rendered context
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRequestDTO(@NotNull GenerationMode mode, @Nullable @Size(max = 8000) String prompt,
        @Nullable @Size(max = 25) List<@Nullable Long> selectedFeedbackThreadIds) {
}
