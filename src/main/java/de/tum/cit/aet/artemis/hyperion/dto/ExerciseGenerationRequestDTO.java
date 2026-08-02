package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to start an agentic whole-exercise generation or adaptation run.
 * <p>
 * What may cross this boundary is deliberately narrow: a <em>name</em> drawn from the admin-defined effort profiles, and numeric bounds that may only tighten. The model and its
 * decoding parameters are not accepted here — editor is a widely granted role, arbitrary model selection is a cost and abuse vector, and Artemis's prompts and gates are co-tuned
 * with the model, so a caller that could set it freely could configure the generator into a state no instructor ever runs.
 *
 * @param mode                      the run intent; see {@link GenerationMode} for why the client states it rather than the server inferring it
 * @param prompt                    optional brief (generate) or feedback (adapt); capped to bound LLM cost/abuse
 * @param selectedFeedbackThreadIds optional review-comment thread ids the adapt run should address; capped to bound the rendered context
 * @param effortProfile             optional name of an admin-configured effort profile; an unknown or unconfigured name is rejected rather than silently defaulted
 * @param maxTokens                 optional per-job token bound; clamped down to the profile's ceiling and never able to raise it
 * @param maxJobDuration            optional wall-clock bound for this run; clamped down to the profile's ceiling and never able to raise it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRequestDTO(@NotNull GenerationMode mode, @Nullable @Size(max = 8000) String prompt,
        @Nullable @Size(max = 25) List<@Nullable Long> selectedFeedbackThreadIds, @Nullable @Size(max = 64) String effortProfile, @Nullable @Positive Long maxTokens,
        @Nullable Duration maxJobDuration) {

    public ExerciseGenerationRequestDTO(GenerationMode mode, @Nullable String prompt, @Nullable List<@Nullable Long> selectedFeedbackThreadIds) {
        this(mode, prompt, selectedFeedbackThreadIds, null, null, null);
    }
}
