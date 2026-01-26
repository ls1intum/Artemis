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
     * Imports a modeling exercise from the source exercise.
     * Note: Example participations are now handled separately by ModelingExerciseImportService.
     *
     * @param sourceExerciseId the id of the source exercise to import from
     * @param targetExercise   the target exercise to import into
     * @return the imported exercise, or empty if the source exercise was not found
     */
    public Optional<ModelingExercise> importModelingExercise(long sourceExerciseId, @NonNull ModelingExercise targetExercise) {
        Optional<ModelingExercise> optionalOriginal = modelingExerciseRepository.findByIdWithGradingCriteria(sourceExerciseId);
        return optionalOriginal.map(modelingExercise -> modelingExerciseImportService.importModelingExercise(modelingExercise, targetExercise));
    }

    /**
     * Imports a modeling exercise from a template exercise into a target exercise.
     *
     * @param templateExercise the template exercise to import from
     * @param targetExercise   the target exercise to import into
     * @return the imported exercise
     */
    public ModelingExercise importModelingExercise(ModelingExercise templateExercise, @NonNull ModelingExercise targetExercise) {
        return modelingExerciseImportService.importModelingExercise(templateExercise, targetExercise);
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
     * Finds a modeling exercise by id with grading criteria, throwing an exception if not found.
     * Note: Example participations are now fetched separately via ExampleParticipationRepository.
     *
     * @param exerciseId the id of the exercise
     * @return the found exercise
     */
    public ModelingExercise findByIdWithGradingCriteriaElseThrow(long exerciseId) {
        return modelingExerciseRepository.findByIdWithGradingCriteriaElseThrow(exerciseId);
    }

    /**
     * Copies a modeling submission with its results and feedback.
     *
     * @param originalSubmission            the original submission to copy
     * @param gradingInstructionCopyTracker mapping from original GradingInstruction IDs to new instances
     * @param targetParticipation           the target example participation for the new submission
     * @return the copied submission
     */
    public Submission copySubmission(Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker,
            de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation targetParticipation) {
        return modelingExerciseImportService.copySubmission(originalSubmission, gradingInstructionCopyTracker, targetParticipation);
    }
}
