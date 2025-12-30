package de.tum.cit.aet.artemis.modeling.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

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
}
