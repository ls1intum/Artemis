package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    Set<Exercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id IN :courseIds
            """)
    Set<Exercise> findByCourseIdsWithCategories(@Param("courseIds") Set<Long> courseIds);

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.categories
            WHERE e.id IN :exerciseIds
            """)
    Set<Exercise> findByExerciseIdsWithCategories(@Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
            	AND e.mode = 'TEAM'
            """)
    Set<Exercise> findAllTeamExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<Exercise> findAllExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.competencies
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findByIdWithCompetencies(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT e FROM Exercise e
                LEFT JOIN FETCH e.competencies c
                LEFT JOIN FETCH c.exercises
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findByIdWithCompetenciesBidirectional(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT e FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.dueDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingDueDate(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT e FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.releaseDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingReleaseDate(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT e FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.assessmentDueDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingAssessmentDueDate(@Param("now") ZonedDateTime now);

    /**
     * Select Exercise for Course ID WHERE there does exist an LtiOutcomeUrl for the current user (-> user has started exercise once using LTI)
     *
     * @param courseId the id of the course
     * @param login    the login of the corresponding user
     * @return list of exercises
     */
    @Query("""
            SELECT e FROM Exercise e
            WHERE e.course.id = :#{#courseId}
            AND EXISTS (
            	SELECT l FROM LtiOutcomeUrl l
            	WHERE e = l.exercise
            	AND l.user.login = :#{#login})
            """)
    Set<Exercise> findByCourseIdWhereLtiOutcomeUrlExists(@Param("courseId") Long courseId, @Param("login") String login);

    @Query("""
             SELECT DISTINCT c FROM Exercise e JOIN e.categories c
             WHERE e.course.id = :#{#courseId}
            """)
    Set<String> findAllCategoryNames(@Param("courseId") Long courseId);

    @Query("""
             SELECT DISTINCT e FROM Exercise e
             LEFT JOIN FETCH e.studentParticipations
             WHERE e.id = :#{#exerciseId}
            """)
    Optional<Exercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig" })
    Optional<Exercise> findWithEagerCategoriesAndTeamAssignmentConfigById(Long exerciseId);

    @Query("""
             SELECT DISTINCT e from Exercise e
             LEFT JOIN FETCH e.exampleSubmissions examplesub
             LEFT JOIN FETCH examplesub.submission exsub
             LEFT JOIN FETCH exsub.results
             WHERE e.id = :#{#exerciseId}
            """)
    Optional<Exercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT e from Exercise e
            LEFT JOIN FETCH e.posts
            LEFT JOIN FETCH e.categories
            WHERE e.id = :#{#exerciseId}
                """)
    Optional<Exercise> findByIdWithDetailsForStudent(@Param("exerciseId") Long exerciseId);

    /**
     * @param courseId - course id of the exercises we want to fetch
     * @return all exercise-ids which belong to the course
     */
    @Query("""
            SELECT e.id FROM Exercise e LEFT JOIN e.course c
            WHERE c.id = :courseId
                """)
    Set<Long> findAllIdsByCourseId(@Param("courseId") Long courseId);

    /**
     * calculates the average score and the participation rate of students for each given individual course exercise
     * by using the last result (rated or not)
     *
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
                e.id,
                AVG(r.score),
                Count(Distinct p.student.id),
                (SELECT count(distinct u.id)
                    FROM User u
                    WHERE
                    e.course.studentGroupName member of u.groups
                    AND e.course.teachingAssistantGroupName not member of u.groups
                    AND (e.course.editorGroupName IS NULL OR  e.course.editorGroupName not member of u.groups)
                    AND e.course.instructorGroupName not member of u.groups
                )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id IN :exerciseIds
                AND e.course.studentGroupName member of p.student.groups
                AND e.course.teachingAssistantGroupName not member of p.student.groups
                AND (e.course.editorGroupName IS NULL OR e.course.editorGroupName not member of p.student.groups)
                AND e.course.instructorGroupName not member of p.student.groups
                AND r.score IS NOT NULL AND r.completionDate IS NOT NULL
                AND s.id = (
                    SELECT max(s2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation.id = s.participation.id
                        AND r2.score IS NOT NULL AND r2.completionDate IS NOT NULL
                    )
            GROUP BY e.id
                """)
    List<Object[]> calculateStatisticsForIndividualCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    /**
     * calculates the average score and the participation rate of students for each given individual course exercise
     * by using the last result (rated or not). This query gets the last result from the participation scores table
     *
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
                e.id,
                AVG(sc.lastScore),
                Count(Distinct p.student.id),
                (   SELECT count(distinct u.id)
                    FROM User u
                    WHERE
                        e.course.studentGroupName member of u.groups
                        AND e.course.teachingAssistantGroupName not member of u.groups
                        AND (e.course.editorGroupName IS NULL OR  e.course.editorGroupName not member of u.groups)
                        AND e.course.instructorGroupName not member of u.groups
                )
            FROM Exercise e JOIN e.studentParticipations p, StudentScore sc
            WHERE e.id IN :exerciseIds
                AND sc.exercise = e AND sc.user = p.student
                AND e.course.studentGroupName member of p.student.groups
                AND e.course.teachingAssistantGroupName not member of p.student.groups
                AND (e.course.editorGroupName IS NULL OR e.course.editorGroupName not member of p.student.groups)
                AND e.course.instructorGroupName not member of p.student.groups
            GROUP BY e.id
            """)
    List<Object[]> calculateExerciseStatisticsForIndividualCourseUsingParticipationTable(@Param("exerciseIds") List<Long> exerciseIds);

    /**
     * calculates the average score and the participation rate of students for each given team course exercise
     * by using the last result (rated or not)
     *
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
                e.id,
                AVG(r.score),
                Count(Distinct p.team.id),
                (   SELECT count(distinct t.id)
                    FROM Team t JOIN t.students st2
                    WHERE st2.id IN (
                        SELECT DISTINCT u.id
                        FROM User u
                        WHERE e.course.studentGroupName member of u.groups
                            AND e.course.teachingAssistantGroupName not member of u.groups
                            AND (e.course.editorGroupName IS NULL OR  e.course.editorGroupName not member of u.groups)
                            AND e.course.instructorGroupName not member of u.groups
                            AND t.exercise.id IN :exerciseIds
                    )
                )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r JOIN p.team.students st
            WHERE e.id IN :exerciseIds
                AND r.score IS NOT NULL AND r.completionDate IS NOT NULL
                AND
                st.id IN (
                    SELECT DISTINCT u.id
                    FROM User u
                    WHERE e.course.studentGroupName member of u.groups
                        AND e.course.teachingAssistantGroupName not member of u.groups
                        AND (e.course.editorGroupName IS NULL OR e.course.editorGroupName not member of u.groups)
                        AND e.course.instructorGroupName not member of u.groups
                    )
                AND
                s.id = (
                    SELECT max(s2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation.id = s.participation.id
                        AND r2.score IS NOT NULL AND r2.completionDate IS NOT NULL
                )
            GROUP BY e.id
            """)
    List<Object[]> calculateStatisticsForTeamCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    /**
     * calculates the average score and the participation rate of students for each given team course exercise
     * by using the last result (rated or not). This query gets the last result from the participation scores table
     *
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
                e.id,
                AVG(ts.lastScore),
                Count(Distinct p.team.id),
                (   SELECT count(distinct t.id)
                    FROM Team t JOIN t.students st2
                    WHERE st2.id IN (
                        SELECT DISTINCT u.id
                        FROM User u
                        WHERE e.course.studentGroupName member of u.groups
                            AND e.course.teachingAssistantGroupName not member of u.groups
                            AND (e.course.editorGroupName IS NULL OR  e.course.editorGroupName not member of u.groups)
                            AND e.course.instructorGroupName not member of u.groups
                    )
                )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.team.students st, TeamScore ts
            WHERE e.id IN :exerciseIds
                AND ts.exercise = e AND ts.team = p.team
                AND st.id IN (
                    SELECT DISTINCT u.id
                    FROM User u
                    WHERE e.course.studentGroupName member of u.groups
                        AND e.course.teachingAssistantGroupName not member of u.groups
                        AND (e.course.editorGroupName IS NULL OR e.course.editorGroupName not member of u.groups)
                        AND e.course.instructorGroupName not member of u.groups
                    )
            GROUP BY e.id
            """)
    List<Object[]> calculateExerciseStatisticsForTeamCourseExercisesUsingParticipationTable(@Param("exerciseIds") List<Long> exerciseIds);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    /**
     * Returns the title of the exercise with the given id.
     *
     * @param exerciseId the id of the exercise
     * @return the name/title of the exercise or null if the exercise does not exist
     */
    @Query("""
            SELECT e.title
            FROM Exercise e
            WHERE e.id = :exerciseId
            """)
    @Cacheable(cacheNames = "exerciseTitle", key = "#exerciseId", unless = "#result == null")
    String getExerciseTitle(@Param("exerciseId") Long exerciseId);

    /**
     * Fetches the exercises for a course
     *
     * @param courseId the course to get the exercises for
     * @return a set of exercises with categories
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exercise e LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    Set<Exercise> getExercisesForCourseManagementOverview(@Param("courseId") Long courseId);

    /**
     * Fetches the exercises for a course with an assessment due date (or due date if without assessment due date) in the future
     *
     * @param courseId the course to get the exercises for
     * @param now      the current date time
     * @return a set of exercises
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND (e.assessmentDueDate IS NULL OR e.assessmentDueDate > :now)
                AND (e.assessmentDueDate IS NOT NULL OR e.dueDate IS NULL OR e.dueDate > :now)
            """)
    Set<Exercise> getActiveExercisesForCourseManagementOverview(@Param("courseId") Long courseId, @Param("now") ZonedDateTime now);

    /**
     * Fetches the exercises for a course with a passed assessment due date (or due date if without assessment due date)
     *
     * @param courseId the course to get the exercises for
     * @param now      the current date time
     * @return a set of exercises
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND (e.assessmentDueDate IS NOT NULL AND e.assessmentDueDate < :now
                OR e.assessmentDueDate IS NULL AND e.dueDate IS NOT NULL AND e.dueDate < :now)
            """)
    List<Exercise> getPastExercisesForCourseManagementOverview(@Param("courseId") Long courseId, @Param("now") ZonedDateTime now);

    /**
     * Finds all exercises that should be part of the summary email (e.g. weekly summary)
     * Exercises should have been released, not yet ended, and the release should be in the time frame [daysAgo,now]
     *
     * @param now     the current date time
     * @param daysAgo the current date time minus the wanted number of days (the used interval) (e.g. for weeklySummaries -> daysAgo = 7)
     * @return all exercises that should be part of the summary (email)
     */
    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.releaseDate IS NOT NULL
                AND e.releaseDate < :now
                AND e.releaseDate > :daysAgo
                AND ((e.dueDate IS NOT NULL AND e.dueDate > :now)
                    OR e.dueDate IS NULL)
            """)
    Set<Exercise> findAllExercisesForSummary(@Param("now") ZonedDateTime now, @Param("daysAgo") ZonedDateTime daysAgo);

    /**
     * Fetches the number of student participations in the given exercise
     *
     * @param exerciseId the id of the exercise to get the amount for
     * @return The number of participations as <code>Long</code>
     */
    @Query("""
            SELECT COUNT(DISTINCT p.student.id)
            FROM Exercise e
                JOIN e.studentParticipations p
            WHERE e.id = :exerciseId
            """)
    Long getStudentParticipationCountById(@Param("exerciseId") Long exerciseId);

    /**
     * Fetches the number of team participations in the given exercise
     *
     * @param exerciseId the id of the exercise to get the amount for
     * @return The number of participations as <code>Long</code>
     */
    @Query("""
            SELECT COUNT(DISTINCT p.team.id)
            FROM Exercise e JOIN e.studentParticipations p
            WHERE e.id = :exerciseId
            """)
    Long getTeamParticipationCountById(@Param("exerciseId") Long exerciseId);

    /**
     * Fetches exercise ids of exercises of a course
     *
     * @param courseId the id of the course the exercises are part of
     * @return a list of ids of exercises
     */
    @Query("""
            SELECT e.id
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    List<Long> getExerciseIdsByCourseId(@Param("courseId") Long courseId);

    @NotNull
    default Exercise findByIdElseThrow(Long exerciseId) throws EntityNotFoundException {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    @NotNull
    default Exercise findByIdWithCompetenciesElseThrow(Long exerciseId) throws EntityNotFoundException {
        return findByIdWithCompetencies(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    @NotNull
    default Exercise findByIdWithCompetenciesBidirectionalElseThrow(Long exerciseId) throws EntityNotFoundException {
        return findByIdWithCompetenciesBidirectional(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Get one exercise by exerciseId with its categories and its team assignment config
     *
     * @param exerciseId the exerciseId of the entity
     * @return the entity
     */
    @NotNull
    default Exercise findByIdWithCategoriesAndTeamAssignmentConfigElseThrow(Long exerciseId) {
        return findWithEagerCategoriesAndTeamAssignmentConfigById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Finds all exercises where the due date is today or in the future
     * (does not return exercises belonging to test courses).
     *
     * @return set of exercises
     */
    default Set<Exercise> findAllExercisesWithCurrentOrUpcomingDueDate() {
        return findAllExercisesWithCurrentOrUpcomingDueDate(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
    }

    /**
     * Find exercise by exerciseId and load participations in this exercise.
     *
     * @param exerciseId the exerciseId of the exercise entity
     * @return the exercise entity
     */
    @NotNull
    default Exercise findByIdWithStudentParticipationsElseThrow(Long exerciseId) {
        return findByIdWithEagerParticipations(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Activates or deactivates the possibility for tutors to assess within the correction round
     *
     * @param exercise - the exercise for which we want to toggle if the second correction round is enabled
     * @return the new state of the second correction
     */
    default boolean toggleSecondCorrection(Exercise exercise) {
        exercise.setSecondCorrectionEnabled(!exercise.getSecondCorrectionEnabled());
        return save(exercise).getSecondCorrectionEnabled();
    }

    /**
     * Finds all exercises of a course where the user has participated in.
     * Currently only used for the data export
     *
     * @param courseId the id of the course
     * @param userId   the id of the user
     * @return a set of exercises the user has participated in with eager participations, submissions, results and feedbacks
     */
    @Query("""
            SELECT e
            FROM Course c
                LEFT JOIN  c.exercises e
                LEFT JOIN FETCH e.studentParticipations p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE c.id = :courseId
                  AND p.student.id = :userId
            """)
    Set<Exercise> getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksByCourseIdAndUserId(long courseId, long userId);
}
