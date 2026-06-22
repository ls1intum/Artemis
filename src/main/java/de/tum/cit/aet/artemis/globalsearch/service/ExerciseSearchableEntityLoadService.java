package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;

/**
 * Loads exercises from the database and builds their searchable DTOs, for the V0→V1 search migration which backfills
 * exercises into the unified collection from the database (the source of truth) rather than copying a stale Weaviate
 * snapshot.
 * <p>
 * The work runs in a read-only transaction because {@link ExerciseSearchableEntityDTO#fromExercise} reads lazily-loaded
 * associations (the exercise's course, its exam for exam exercises, and subtype-specific fields) and open-session-in-view
 * is disabled. Exercise ids that no longer exist in the database are skipped, so exercises deleted since the legacy index
 * are not re-created in search.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class ExerciseSearchableEntityLoadService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseSearchableEntityLoadService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Loads the given exercises from the database and builds their searchable DTOs. Ids that do not (or no longer) exist
     * in the database are silently skipped.
     *
     * @param exerciseIds the exercise ids to load
     * @return the searchable DTOs for the exercises that exist, in no particular order
     */
    @Transactional(readOnly = true)
    public List<ExerciseSearchableEntityDTO> loadExerciseDtos(Collection<Long> exerciseIds) {
        List<ExerciseSearchableEntityDTO> dtos = new ArrayList<>();
        for (Long exerciseId : exerciseIds) {
            exerciseRepository.findById(exerciseId).ifPresent(exercise -> dtos.add(ExerciseSearchableEntityDTO.fromExercise(exercise)));
        }
        return dtos;
    }
}
