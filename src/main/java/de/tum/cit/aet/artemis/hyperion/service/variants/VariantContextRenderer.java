package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: renders the source exercise as LLM context for the ANALYZING phase
 * (plan Section 2.3). One implementation per supported exercise type, bundled in {@link VariantTypeAdapters}.
 */
public interface VariantContextRenderer {

    /**
     * Renders the complete source-exercise context that the planner prompt consumes.
     *
     * TODO (Sonnet, programming): delegate to the existing
     * HyperionProgrammingExerciseContextRendererService.renderContext(exercise) — problem statement +
     * template/solution/test repo contents (plan Section 3, ANALYZING row). Do not re-implement rendering.
     *
     * TODO (Sonnet, quiz): implement a new small renderer that serializes the quiz — questions, options,
     * mappings, scoring types — using the quiz domain model (MultipleChoiceQuestion, DragAndDropQuestion,
     * ShortAnswerQuestion) (plan Section 4, ANALYZING row). Keep it deterministic and compact (token budget).
     *
     * @param source the source exercise (already authorization-checked by the resource)
     * @return the rendered context string for the PLANNING prompt
     */
    String renderContext(Exercise source);
}
