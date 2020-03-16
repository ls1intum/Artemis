package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ModelingExerciseService(ModelingExerciseRepository modelingExerciseRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * Get one modeling exercise by id.
     *
     * @param exerciseId the id of the entity
     * @return the entity
     */
    public ModelingExercise findOne(Long exerciseId) {
        log.debug("Request to get Modeling Exercise : {}", exerciseId);
        return modelingExerciseRepository.findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + exerciseId + "\" does not exist"));
    }
}
