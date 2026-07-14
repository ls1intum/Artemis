package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Bundle of the five capability adapters one exercise type contributes. Implementations are Spring beans;
 * {@link VariantTypeRegistry} resolves the bundle for the source exercise's type via the standard Spring idiom
 * (inject {@code List<VariantTypeAdapters>}, pick by {@code supports(...)}).
 *
 * Adding a new exercise type (modeling/text/file-upload) = implementing this interface with thin wrappers around
 * the type's existing import/validation services; the pipeline, planner, agent loop, job infra, REST API, and
 * client are untouched.
 */
public interface VariantTypeAdapters extends VariantContextRenderer, ExerciseProvisioner, VariantToolsetFactory, VariantVerifier, VariantFinalizer {

    /**
     * @return the exercise type this bundle supports (used by {@link VariantTypeRegistry#resolve})
     */
    ExerciseType supportedExerciseType();
}
