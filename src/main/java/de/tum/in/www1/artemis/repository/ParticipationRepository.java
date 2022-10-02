package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    @Query("""
            SELECT DISTINCT p FROM Participation p
            LEFT JOIN FETCH p.results
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results
            WHERE p.id = :#{#participationId}
            """)
    Optional<Participation> findByIdWithResultsAndSubmissionsResults(@Param("participationId") Long participationId);

    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.id = :participationId
                AND (s.id = (SELECT max(id) FROM p.submissions) OR s.id = NULL)
            """)
    Optional<Participation> findByIdWithLatestSubmissionAndResult(@Param("participationId") Long participationId);

    @Query("""
            SELECT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.id = :#{#participationId}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    Optional<Participation> findWithEagerLegalSubmissionsById(@Param("participationId") Long participationId);

    @NotNull
    default Participation findByIdWithLegalSubmissionsElseThrow(long participationId) {
        return findWithEagerLegalSubmissionsById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    @NotNull
    default Participation findByIdElseThrow(long participationId) {
        return findById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    @Query("""
            SELECT max(p.individualDueDate)
            FROM Participation p
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.individualDueDate IS NOT null
            """)
    Optional<ZonedDateTime> findLatestIndividualDueDate(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT p
            FROM Participation p
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.individualDueDate IS NOT null
            """)
    Set<Participation> findWithIndividualDueDateByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Removes all individual due dates of participations for which the individual due date is before the updated due date of the exercise.
     *
     * Only considers regular course exercises when the due date actually changed.
     * @param exercise for which the participations should be updated.
     * @param oldDueDate the regular due date of the exercise before the update.
     */
    default void removeIndividualDueDatesIfBeforeDueDate(final Exercise exercise, final ZonedDateTime oldDueDate) {
        if (exercise.isCourseExercise() && !Objects.equals(exercise.getDueDate(), oldDueDate)) {
            removeIndividualDueDatesIfBeforeDueDate(exercise);
        }
    }

    /**
     * Removes all individual due dates of participations for which the individual due date is before the exercise due date.
     * @param exercise for which the participations should be updated.
     */
    private void removeIndividualDueDatesIfBeforeDueDate(Exercise exercise) {
        final ZonedDateTime exerciseDueDate = exercise.getDueDate();
        final Set<Participation> participations = findWithIndividualDueDateByExerciseId(exercise.getId());
        final List<Participation> changedParticipations = new ArrayList<>();

        for (final var participation : participations) {
            if (exerciseDueDate == null || participation.getIndividualDueDate().isBefore(exerciseDueDate)) {
                participation.setIndividualDueDate(null);
                changedParticipations.add(participation);
            }
        }

        saveAllAndFlush(changedParticipations);
    }
}
