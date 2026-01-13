package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ParticipationRepository extends ArtemisJpaRepository<Participation, Long> {

    /**
     * Count the number of participations for a given set of exercises.
     *
     * @param exerciseIds the id of the exercises (e.g. all in a course)
     * @return the number of participations in these exercises
     */
    @Query("""
            SELECT COUNT(p)
            FROM Participation p
            WHERE p.exercise.id IN :exerciseIds
            """)
    long countByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Count the number of participations for a given exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the number of participations in the exercise
     */
    @Query("""
            SELECT COUNT(p)
            FROM Participation p
            WHERE p.exercise.id = :exerciseId
            """)
    long countByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Find all participations (of any type) for a given exercise.
     * This includes StudentParticipation, TemplateProgrammingExerciseParticipation,
     * SolutionProgrammingExerciseParticipation, etc.
     *
     * @param exerciseId the id of the exercise
     * @return list of all participations referencing the exercise
     */
    @Query("""
            SELECT p
            FROM Participation p
            WHERE p.exercise.id = :exerciseId
            """)
    List<Participation> findAllByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Find all student participations for a course with their latest submission and results.
     * Includes both rated (before due date) and practice (after due date) participations.
     *
     * @param courseId the id of the course
     * @return list of student participations with submissions and results
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH p.exercise e
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
                LEFT JOIN FETCH p.student
            WHERE e.course.id = :courseId
            """)
    List<StudentParticipation> findAllWithLatestSubmissionAndResultByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.id = :participationId
            """)
    Optional<Participation> findByIdWithSubmissionsResults(@Param("participationId") long participationId);

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
            """)
    Optional<Participation> findWithEagerSubmissionsById(@Param("participationId") long participationId);

    @NonNull
    default Participation findByIdWithSubmissionsElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerSubmissionsById(participationId), participationId);
    }

    @NonNull
    default Participation findByIdWithSubmissionsResultsElseThrow(long participationId) {
        return getValueElseThrow(findByIdWithSubmissionsResults(participationId), participationId);
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

    @NonNull
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
