package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseHintRepository;

/**
 * Service Implementation for managing {@link ExerciseHint}.
 */
@Service
@Transactional
public class ExerciseHintService {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintService.class);

    private final ExerciseHintRepository exerciseHintRepository;

    public ExerciseHintService(ExerciseHintRepository exerciseHintRepository) {
        this.exerciseHintRepository = exerciseHintRepository;
    }

    /**
     * Save a exerciseHint.
     *
     * @param exerciseHint the entity to save.
     * @return the persisted entity.
     */
    public ExerciseHint save(ExerciseHint exerciseHint) {
        log.debug("Request to save ExerciseHint : {}", exerciseHint);
        return exerciseHintRepository.save(exerciseHint);
    }

    /**
     * Get all the exerciseHints.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ExerciseHint> findAll() {
        log.debug("Request to get all ExerciseHints");
        return exerciseHintRepository.findAll();
    }

    /**
     * Get one exerciseHint by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ExerciseHint> findOne(Long id) {
        log.debug("Request to get ExerciseHint : {}", id);
        return exerciseHintRepository.findById(id);
    }

    /**
     * Get all hints of an exercise;
     * @param exerciseId of exercise.
     * @return hints of provided exercise.
     */
    @Transactional(readOnly = true)
    public Set<ExerciseHint> findByExerciseId(Long exerciseId) {
        log.debug("Request to get ExerciseHints by Exercise id : {}", exerciseId);
        return exerciseHintRepository.findByExerciseId(exerciseId);
    }

    /**
     * Delete the exerciseHint by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete ExerciseHint : {}", id);
        exerciseHintRepository.deleteById(id);
    }
}
