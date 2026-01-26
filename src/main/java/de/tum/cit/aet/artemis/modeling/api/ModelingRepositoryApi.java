package de.tum.cit.aet.artemis.modeling.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

/**
 * API for modeling repository operations.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingRepositoryApi extends AbstractModelingApi {

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ModelingRepositoryApi(ModelingExerciseRepository modelingExerciseRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * Finds a modeling exercise by id with competencies and grading criteria.
     * Note: Example participations are now fetched separately via ExampleParticipationRepository.
     *
     * @param exerciseId the id of the exercise
     * @return the found exercise, or empty if not found
     */
    public Optional<ModelingExercise> findByIdWithCompetenciesAndGradingCriteria(long exerciseId) {
        return modelingExerciseRepository.findByIdWithCompetenciesAndGradingCriteria(exerciseId);
    }

    public Optional<ModelingExercise> findForVersioningById(long exerciseId) {
        return modelingExerciseRepository.findForVersioningById(exerciseId);
    }

    /**
     * Finds a modeling exercise by id, throwing an exception if not found.
     *
     * @param exerciseId the id of the exercise
     * @return the found exercise
     */
    public ModelingExercise findByIdElseThrow(long exerciseId) {
        return modelingExerciseRepository.findByIdElseThrow(exerciseId);
    }
}
