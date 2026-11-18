package de.tum.cit.aet.artemis.modeling.api;

import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseImportService;

/**
 * API for modeling exercise import operations.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingExerciseImportApi extends AbstractModelingApi {

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingExerciseImportService modelingExerciseImportService;

    public ModelingExerciseImportApi(ModelingExerciseRepository modelingExerciseRepository, ModelingExerciseImportService modelingExerciseImportService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
    }

    /**
     * Imports a modeling exercise, taking the content from the source exercise (looked up by id).
     *
     * @param sourceExerciseId the id of the source exercise to import from
     * @param newExercise      the exercise to build; carries the destination and any overrides
     * @return the imported exercise, or empty if the source exercise was not found
     */
    public Optional<ModelingExercise> importModelingExercise(long sourceExerciseId, @NonNull ModelingExercise newExercise) {
        Optional<ModelingExercise> optionalSource = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(sourceExerciseId);
        return optionalSource.map(sourceExercise -> modelingExerciseImportService.importModelingExercise(newExercise, sourceExercise));
    }

    /**
     * Imports a modeling exercise, taking the content from {@code sourceExercise}.
     *
     * @param newExercise    the exercise to build; carries the destination and any overrides
     * @param sourceExercise the original exercise whose content is copied
     * @return the imported exercise
     */
    public ModelingExercise importModelingExercise(@NonNull ModelingExercise newExercise, ModelingExercise sourceExercise) {
        return modelingExerciseImportService.importModelingExercise(newExercise, sourceExercise);
    }

    /**
     * Finds a unique modeling exercise with competencies by title and course id.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course
     * @return the found exercise, or empty if not found
     * @throws NoUniqueQueryException if more than one exercise is found
     */
    public Optional<ModelingExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        return modelingExerciseRepository.findUniqueWithCompetenciesByTitleAndCourseId(title, courseId);
    }

    /**
     * Finds a modeling exercise by id with example submissions and results, throwing an exception if not found.
     *
     * @param exerciseId the id of the exercise
     * @return the found exercise
     */
    public ModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(exerciseId);
    }

    /**
     * Copies a modeling submission with its results and feedback.
     *
     * @param originalSubmission            the original submission to copy
     * @param gradingInstructionCopyTracker mapping from original GradingInstruction IDs to new instances
     * @return the copied submission
     */
    public Submission copySubmission(Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        return modelingExerciseImportService.copySubmission(originalSubmission, gradingInstructionCopyTracker);
    }
}
