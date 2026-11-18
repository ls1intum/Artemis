package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Bundle of the five capability adapters one exercise type contributes. Implementations are Spring beans;
 * {@link VariantTypeRegistryService} resolves the bundle for the source exercise's type via the standard Spring idiom
 * (inject {@code List<VariantTypeAdapters>}, pick by {@code supports(...)}).
 *
 * Adding a new exercise type (modeling/text/file-upload) = implementing this interface with thin wrappers around
 * the type's existing import/validation services; the pipeline, planner, agent loop, job infra, REST API, and
 * client are untouched.
 */
public interface VariantTypeAdapters extends VariantContextRenderer, ExerciseProvisioner, VariantToolsetFactory, VariantVerifier, VariantFinalizer {

    /**
     * @return the exercise type this bundle supports (used by {@link VariantTypeRegistryService#resolve})
     */
    ExerciseType supportedExerciseType();

    /**
     * Refines {@link #supportedExerciseType()} for the individual exercise: a type may be supported in general
     * while a particular exercise of that type is not (see {@code QuizVariantAdapterService} for drag-and-drop quizzes).
     * The client hides the generation button for unsupported exercises; the REST boundary rejects them.
     *
     * @param exercise the source exercise, already known to be of {@link #supportedExerciseType()}
     * @return true when a variant can be generated from this exercise; the default is "every exercise of the type"
     */
    default boolean supportsExercise(Exercise exercise) {
        return true;
    }
}
