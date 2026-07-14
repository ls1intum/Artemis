package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: renders the source exercise as LLM context for the ANALYZING phase.
 * One implementation per supported exercise type, bundled in {@link VariantTypeAdapters}.
 */
public interface VariantContextRenderer {

    /**
     * Renders the complete source-exercise context that the planner prompt consumes. Programming delegates to
     * {@code HyperionProgrammingExerciseContextRendererService} (problem statement + template/solution/test repo
     * contents); quiz serializes questions, options, mappings and scoring types from the quiz domain model.
     *
     * @param source the source exercise (already authorization-checked by the resource)
     * @return the rendered context string for the PLANNING prompt
     */
    String renderContext(Exercise source);
}
