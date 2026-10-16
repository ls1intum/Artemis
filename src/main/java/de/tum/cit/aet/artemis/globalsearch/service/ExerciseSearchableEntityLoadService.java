package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;

/**
 * Loads an exercise from the database and builds its searchable DTO, for the outbox dispatcher's re-derivation of an
 * index row.
 * <p>
 * The exercise is fetched with the associations {@link ExerciseSearchableEntityDTO#fromExercise} reads (course, and the
 * exam for exam exercises) eagerly loaded, so the DTO can be built without a Hibernate session (open-session-in-view is
 * disabled).
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
     * Loads a single exercise for dispatch-time re-derivation (used by {@link SearchableEntityResolver}) and builds its
     * searchable DTO. It returns empty <b>only</b> when the exercise row is absent from the database. A loading or mapping
     * failure propagates, so the outbox dispatcher retries the write with backoff instead of mistaking the failure for a
     * deletion and dropping the row.
     *
     * @param exerciseId the exercise id
     * @return the DTO if the exercise exists, or {@link Optional#empty()} if no such row exists
     */
    public Optional<ExerciseSearchableEntityDTO> loadExerciseDtoForResolve(long exerciseId) {
        return exerciseRepository.findAllForSearchMigrationWithCourseAndExam(List.of(exerciseId)).stream().findFirst().map(ExerciseSearchableEntityDTO::fromExercise);
    }
}
