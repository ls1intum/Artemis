package de.tum.cit.aet.artemis.modeling.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
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
     *
     * @param sourceExerciseId the id of the source exercise to import from
     * @param targetExercise   the target exercise to import into
     * @return the imported exercise, or empty if the source exercise was not found
     */
    public Optional<ModelingExercise> importModelingExercise(long sourceExerciseId, ModelingExercise targetExercise) {
        Optional<ModelingExercise> optionalOriginal = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteria(sourceExerciseId);
        if (optionalOriginal.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(modelingExerciseImportService.importModelingExercise(optionalOriginal.get(), targetExercise));
    }

    /**
     * Imports a modeling exercise from a template exercise into a target exercise.
     *
     * @param templateExercise the template exercise to import from
     * @param targetExercise   the target exercise to import into
     * @return the imported exercise
     */
    public ModelingExercise importModelingExercise(ModelingExercise templateExercise, ModelingExercise targetExercise) {
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
     * Finds a modeling exercise by id with example submissions and results, throwing an exception if not found.
     *
     * @param exerciseId the id of the exercise
     * @return the found exercise
     */
    public ModelingExercise findByIdWithExampleSubmissionsAndResultsElseThrow(long exerciseId) {
        return modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(exerciseId);
    }
}
