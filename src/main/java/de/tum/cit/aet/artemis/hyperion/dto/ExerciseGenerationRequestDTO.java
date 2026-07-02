package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to start an agentic whole-exercise generation or adaptation run.
 * <p>
 * The {@code mode} is explicit and never inferred from the exercise's contents: one endpoint and one engine drive both {@link GenerationMode#GENERATE} and
 * {@link GenerationMode#ADAPT}, and only the client knows which the instructor intends. When omitted it defaults to {@link GenerationMode#GENERATE} (see {@link #effectiveMode()}).
 *
 * @param mode                     the explicit run intent (generate a fresh exercise vs. adapt an existing one); defaults to {@link GenerationMode#GENERATE} when absent
 * @param prompt                   optional brief (generate) or feedback (adapt); capped to bound LLM cost/abuse
 * @param selectedFeedbackThreadIds optional review-comment thread ids the adapt run should address; capped to bound the rendered context
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRequestDTO(@Nullable GenerationMode mode, @Nullable @Size(max = 8000) String prompt,
        @Nullable @Size(max = 25) List<@Nullable Long> selectedFeedbackThreadIds) {

    /**
     * Convenience constructor for a prompt-only request in the default {@link GenerationMode#GENERATE} mode (no selected feedback threads).
     *
     * @param prompt the optional brief
     */
    public ExerciseGenerationRequestDTO(@Nullable String prompt) {
        this(null, prompt, null);
    }

    /**
     * @return the requested mode, defaulting to {@link GenerationMode#GENERATE} when the client did not send one (the safe, non-destructive default)
     */
    public GenerationMode effectiveMode() {
        return mode != null ? mode : GenerationMode.GENERATE;
    }
}
