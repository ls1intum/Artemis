package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;

/**
 * Loads exercises from the database and builds their searchable DTOs, for the V0→V1 search migration which backfills
 * exercises into the unified collection from the database (the source of truth) rather than copying a stale Weaviate
 * snapshot.
 * <p>
 * The exercises are fetched with the associations {@link ExerciseSearchableEntityDTO#fromExercise} reads (course, and the
 * exam for exam exercises) eagerly loaded, so the DTOs can be built without a Hibernate session (open-session-in-view is
 * disabled) and without a per-exercise lazy load. Exercise ids that no longer exist in the database are skipped, so
 * exercises deleted since the legacy index are not re-created in search.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class ExerciseSearchableEntityLoadService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseSearchableEntityLoadService.class);

    private final ExerciseRepository exerciseRepository;

    public ExerciseSearchableEntityLoadService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Loads the given exercises from the database (in a single batched query) and builds their searchable DTOs. Ids that
     * do not (or no longer) exist in the database are silently skipped.
     *
     * @param exerciseIds the exercise ids to load
     * @return the searchable DTOs for the exercises that exist, in no particular order
     */
    public List<ExerciseSearchableEntityDTO> loadExerciseDtos(Collection<Long> exerciseIds) {
        List<Exercise> exercises = exerciseRepository.findAllForSearchMigrationWithCourseAndExam(exerciseIds);
        List<ExerciseSearchableEntityDTO> dtos = new ArrayList<>();
        for (Exercise exercise : exercises) {
            try {
                dtos.add(ExerciseSearchableEntityDTO.fromExercise(exercise));
            }
            catch (Exception exception) {
                // A single exercise that cannot be mapped must not abort the whole migration (which would then exhaust
                // its retries and never complete). Skip it; the live indexing path will index it on its next edit.
                log.warn("V0→V1: Skipping exercise {} that could not be mapped for the search migration: {}", exercise.getId(), exception.getMessage());
            }
        }
        return dtos;
    }
}
