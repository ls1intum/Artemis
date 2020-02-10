package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseHintRepository;

/**
 * Service Implementation for managing {@link ExerciseHint}.
 */
@Service
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
    public Optional<ExerciseHint> findOne(Long id) {
        log.debug("Request to get ExerciseHint : {}", id);
        return exerciseHintRepository.findById(id);
    }

    /**
     * Get all hints of an exercise;
     * @param exerciseId of exercise.
     * @return hints of provided exercise.
     */
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

    /**
     * Copies the hints of an exercise to a new target exercise by cloning the hint objects and saving them
     * resulting in new IDs for the copied hints. The contents stay the same. On top of that, all hints in the
     * problem statement of the target exercise get replaced by the new IDs.
     *
     * @param template The template exercise containing the hints that should be copied
     * @param target The new target exercise, to which all hints should get copied to.
     */
    public void copyExerciseHints(final Exercise template, final Exercise target) {
        final Map<Long, Long> hintIdMapping = new HashMap<>();
        target.setExerciseHints(template.getExerciseHints().stream().map(hint -> {
            final var copiedHint = new ExerciseHint();
            copiedHint.setExercise(target);
            copiedHint.setContent(hint.getContent());
            copiedHint.setTitle(hint.getTitle());
            exerciseHintRepository.save(copiedHint);
            hintIdMapping.put(hint.getId(), copiedHint.getId());
            return copiedHint;
        }).collect(Collectors.toSet()));

        String patchedStatement = target.getProblemStatement();
        for (final var idMapping : hintIdMapping.entrySet()) {
            // Replace any old hint ID in the imported statement with the new hint ID
            // $1 --> everything before the old hint ID; $3 --> Everything after the old hint ID --> $1 newHintID $3
            final var replacement = "$1" + idMapping.getValue() + "$3";
            patchedStatement = patchedStatement.replaceAll("(\\{[^}]*)(" + idMapping.getKey() + ")([^}]*\\})", replacement);
        }
        target.setProblemStatement(patchedStatement);
    }
}
