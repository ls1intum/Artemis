package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.participation.Participation;

@Profile(PROFILE_CORE)
@Repository
public interface ParticipationRepository extends ArtemisJpaRepository<Participation, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM Participation p
                LEFT JOIN FETCH p.results
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.id = :participationId
            """)
    Optional<Participation> findByIdWithResultsAndSubmissionsResults(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.id = :participationId
                AND (s.id = (SELECT MAX(s2.id) FROM p.submissions s2) OR s.id IS NULL)
            """)
    Optional<Participation> findByIdWithLatestSubmissionAndResult(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.id = :participationId
                AND (s.id = (SELECT MAX(s2.id) FROM p.submissions s2) OR s.id IS NULL)
            """)
    Optional<Participation> findByIdWithLatestSubmission(@Param("participationId") long participationId);

    default Participation findByIdWithLatestSubmissionElseThrow(Long participationId) {
        return getValueElseThrow(findByIdWithLatestSubmission(participationId), participationId);
    }

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.domain.enumeration.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    Optional<Participation> findWithEagerLegalSubmissionsById(@Param("participationId") long participationId);

    @NotNull
    default Participation findByIdWithLegalSubmissionsElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerLegalSubmissionsById(participationId), participationId);
    }

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.id = :participationId
            """)
    Optional<Participation> findWithEagerSubmissionsByIdWithTeamStudents(@Param("participationId") Long participationId);

    @NotNull
    default Participation findWithEagerSubmissionsByIdWithTeamStudentsElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerSubmissionsByIdWithTeamStudents(participationId), participationId);
    }

    @Query("""
            SELECT MAX(p.individualDueDate)
            FROM Participation p
            WHERE p.exercise.id = :exerciseId
                AND p.individualDueDate IS NOT NULL
            """)
    Optional<ZonedDateTime> findLatestIndividualDueDate(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT MIN(p.individualDueDate)
            FROM Participation p
            WHERE p.exercise.id = :exerciseId
                AND p.individualDueDate IS NOT NULL
            """)
    Optional<ZonedDateTime> findEarliestIndividualDueDate(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT p
            FROM Participation p
            WHERE p.exercise.id = :exerciseId
                AND p.individualDueDate IS NOT NULL
            """)
    Set<Participation> findWithIndividualDueDateByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
                SELECT p
                FROM Participation p
                    LEFT JOIN FETCH p.exercise e
                    LEFT JOIN FETCH e.buildConfig
                WHERE p.id = :participationId
            """)
    Optional<Participation> findWithProgrammingExerciseWithBuildConfigById(@Param("participationId") long participationId);

    Set<Participation> findByExerciseId(long exerciseId);

    /**
     * Removes all individual due dates of participations for which the individual due date is before the updated due date of the exercise.
     * <p>
     * Only considers regular course exercises when the due date actually changed.
     *
     * @param exercise   for which the participations should be updated.
     * @param oldDueDate the regular due date of the exercise before the update.
     */
    default void removeIndividualDueDatesIfBeforeDueDate(final Exercise exercise, final ZonedDateTime oldDueDate) {
        if (exercise.isCourseExercise() && !Objects.equals(exercise.getDueDate(), oldDueDate)) {
            removeIndividualDueDatesIfBeforeDueDate(exercise);
        }
    }

    /**
     * Removes all individual due dates of participations for which the individual due date is before the exercise due date.
     *
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
