package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.NonQuizExerciseCalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.web.ExamResource;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCountDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseRepository extends ArtemisJpaRepository<Exercise, Long> {

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
            WHERE e.course.id IN :courseIds
            """)
    Set<Exercise> findByCourseIds(@Param("courseIds") Set<Long> courseIds);

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
            	AND e.mode = de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.TEAM
            """)
    Set<Exercise> findAllTeamExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<Exercise> findAllExercisesByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND (e.releaseDate <=:now  OR e.releaseDate IS NULL)
            """)
    Set<Exercise> findAllReleasedExercisesByCourseId(@Param("courseId") long courseId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findWithCompetenciesById(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.dueDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingDueDate(@Param("now") ZonedDateTime now);

    /**
     * Return the number of active exercises, grouped by exercise type
     * If for one exercise type no exercise is active, the result WILL NOT contain an entry for that exercise type.
     *
     * @param now the current time
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND (e.dueDate >= :now OR e.dueDate IS NULL)
            	AND (e.releaseDate <= :now OR e.releaseDate IS NULL)
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countActiveExercisesGroupByExerciseType(@Param("now") ZonedDateTime now);

    /**
     * Return the number of exercises, grouped by exercise type
     * If for one exercise type no exercise exists, the result WILL NOT contain an entry for that exercise type.
     *
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countExercisesGroupByExerciseType();

    /**
     * Return the number of exercises that will end between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will end, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate the minimum due date
     * @param maxDate the maximum due date
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.dueDate >= :minDate
            	AND e.dueDate <= :maxDate
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countExercisesWithEndDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Return the number of students that are part of an exercise that will end between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will end, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate the minimum due date
     * @param maxDate the maximum due date
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(DISTINCT user.id)
            )
            FROM Exercise e
                JOIN User user ON e.course.studentGroupName MEMBER OF user.groups
            WHERE e.course.testCourse = FALSE
            	AND e.dueDate >= :minDate
            	AND e.dueDate <= :maxDate
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countStudentsInExercisesWithDueDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Return the number of active students that are part of an exercise that will end between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will end, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate     the minimum due date
     * @param maxDate     the maximum due date
     * @param activeSince timestamp defining when a user is considered active
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(DISTINCT user.id)
            )
            FROM Exercise e
                JOIN User user ON e.course.studentGroupName MEMBER OF user.groups
            WHERE e.course.testCourse = FALSE
            	AND e.dueDate >= :minDate
                AND e.dueDate <= :maxDate
                AND user.login NOT LIKE '%test%'
                AND EXISTS (
                    SELECT 1
                    FROM Submission submission
                        JOIN submission.participation participation
                    WHERE submission.submissionDate >= :activeSince
                        AND participation.student.id = user.id
                )
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithDueDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate,
            @Param("maxDate") ZonedDateTime maxDate, @Param("activeSince") ZonedDateTime activeSince);

    /**
     * Return the number of exercises that will be released between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will be released, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate the minimum release date
     * @param maxDate the maximum release date
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(e.id)
            )
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.releaseDate >= :minDate
            	AND e.releaseDate <= :maxDate
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countExercisesWithReleaseDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Return the number of students that are part of an exercise that will be released between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will be released, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate the minimum release date
     * @param maxDate the maximum release date
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(DISTINCT user.id)
            )
            FROM Exercise e
                JOIN User user ON e.course.studentGroupName MEMBER OF user.groups
            WHERE e.course.testCourse = FALSE
            	AND e.releaseDate >= :minDate
            	AND e.releaseDate <= :maxDate
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate,
            @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Return the number of active students that are part of an exercise that will be release between minDate and maxDate, grouped by exercise type
     * If for one exercise type no exercise will be released, the result WILL NOT contain an entry for that exercise type.
     *
     * @param minDate     the minimum release date
     * @param maxDate     the maximum release date
     * @param activeSince timestamp defining when a user is considered active
     * @return a list of ExerciseTypeMetricsEntries, one for each exercise type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry(
                TYPE(e),
                COUNT(DISTINCT user.id)
            )
            FROM Exercise e
                JOIN User user ON e.course.studentGroupName MEMBER OF user.groups
            WHERE e.course.testCourse = FALSE
            	AND e.releaseDate >= :minDate
                AND e.releaseDate <= :maxDate
                AND user.login NOT LIKE '%test%'
                AND EXISTS (
                    SELECT 1
                    FROM Submission submission
                        JOIN submission.participation participation
                    WHERE submission.submissionDate >= :activeSince
                        AND participation.student.id = user.id
                )
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(@Param("minDate") ZonedDateTime minDate,
            @Param("maxDate") ZonedDateTime maxDate, @Param("activeSince") ZonedDateTime activeSince);

    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN FETCH e.plagiarismDetectionConfig c
                LEFT JOIN FETCH e.studentParticipations p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE e.dueDate >= :time
                AND c.continuousPlagiarismControlEnabled = TRUE
            """)
    Set<Exercise> findAllExercisesWithDueDateOnOrAfterAndContinuousPlagiarismControlEnabledIsTrue(@Param("time") ZonedDateTime time);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.releaseDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingReleaseDate(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.testCourse = FALSE
            	AND e.assessmentDueDate >= :now
            ORDER BY e.dueDate ASC
            """)
    Set<Exercise> findAllExercisesWithCurrentOrUpcomingAssessmentDueDate(@Param("now") ZonedDateTime now);

    /**
     * Select Exercise for Course ID WHERE there does exist an LtiResourceLaunch for the current user (-> user has started exercise once using LTI)
     *
     * @param courseId the id of the course
     * @param login    the login of the corresponding user
     * @return set of exercises
     */
    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND EXISTS (
            	    SELECT l
                    FROM LtiResourceLaunch l
            	    WHERE e = l.exercise
            	        AND l.user.login = :login
                )
            """)
    Set<Exercise> findByCourseIdWhereLtiResourceLaunchExists(@Param("courseId") Long courseId, @Param("login") String login);

    @Query("""
            SELECT DISTINCT c
            FROM Exercise e
               JOIN e.categories c
            WHERE e.course.id = :courseId
            """)
    Set<String> findAllCategoryNames(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
                LEFT JOIN FETCH e.studentParticipations
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig" })
    Optional<Exercise> findWithEagerCategoriesAndTeamAssignmentConfigById(Long exerciseId);

    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
                LEFT JOIN FETCH e.exampleSubmissions examplesub
                LEFT JOIN FETCH examplesub.submission exsub
                LEFT JOIN FETCH exsub.results
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
                LEFT JOIN FETCH e.categories
                LEFT JOIN FETCH e.submissionPolicy
            WHERE e.id = :exerciseId
            """)
    Optional<Exercise> findByIdWithDetailsForStudent(@Param("exerciseId") Long exerciseId);

    /**
     * @param courseId - course id of the exercises we want to fetch
     * @return all exercise-ids which belong to the course
     */
    @Query("""
            SELECT e.id
            FROM Exercise e
            WHERE e.course.id = :courseId
            """)
    Set<Long> findExerciseIdsByCourseId(@Param("courseId") Long courseId);

    /**
     * @param courseId - course id of the exercises we want to fetch
     * @return all exercise-ids which belong to the course and have manual assessment enabled, i.e. text, modeling, file upload and programming exercises with manual or
     *         semi-automatic assessment
     */
    @Query("""
            SELECT e.id
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND e.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                AND TYPE(e) <> de.tum.cit.aet.artemis.quiz.domain.QuizExercise
            """)
    Set<Long> findExerciseIdsWithManualAssessmentByCourseId(@Param("courseId") Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    /**
     * Returns the title of the exercise with the given id.
     *
     * @param exerciseId the id of the exercise
     * @return the name/title of the exercise or null if the exercise does not exist
     */
    @Query("""
            SELECT CASE WHEN exerciseGroup IS NOT NULL
                THEN exerciseGroup.title
                ELSE exercise.title
            END AS title
            FROM Exercise exercise
                LEFT JOIN exercise.exerciseGroup exerciseGroup
            WHERE exercise.id = :exerciseId
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
            FROM Exercise e
                LEFT JOIN FETCH e.categories
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
     * @return a list of exercises
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
            WHERE e.course.id = :courseId
                AND (
                    e.assessmentDueDate IS NOT NULL AND e.assessmentDueDate < :now
                    OR e.assessmentDueDate IS NULL AND e.dueDate IS NOT NULL AND e.dueDate < :now
                )
            """)
    List<Exercise> getPastExercisesForCourseManagementOverview(@Param("courseId") Long courseId, @Param("now") ZonedDateTime now);

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
            FROM Exercise e
                JOIN e.studentParticipations p
            WHERE e.id = :exerciseId
            """)
    Long getTeamParticipationCountById(@Param("exerciseId") Long exerciseId);

    @NonNull
    default Exercise findWithCompetenciesByIdElseThrow(long exerciseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithCompetenciesById(exerciseId), exerciseId);
    }

    /**
     * Get one exercise by exerciseId with its categories and its team assignment config
     *
     * @param exerciseId the exerciseId of the entity
     * @return the entity
     */
    @NonNull
    default Exercise findByIdWithCategoriesAndTeamAssignmentConfigElseThrow(Long exerciseId) {
        return getValueElseThrow(findWithEagerCategoriesAndTeamAssignmentConfigById(exerciseId), exerciseId);
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
     * Finds all exercises where the due date is yesterday, today or in the future and continuous plagiarism control is enabled
     * (does not return exercises belonging to test courses).
     *
     * @return set of exercises
     */
    default Set<Exercise> findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue() {
        var yesterday = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(1);
        return findAllExercisesWithDueDateOnOrAfterAndContinuousPlagiarismControlEnabledIsTrue(yesterday);
    }

    /**
     * Find exercise by exerciseId and load participations in this exercise.
     *
     * @param exerciseId the exerciseId of the exercise entity
     * @return the exercise entity
     */
    @NonNull
    default Exercise findByIdWithStudentParticipationsElseThrow(Long exerciseId) {
        return getValueElseThrow(findByIdWithEagerParticipations(exerciseId), exerciseId);
    }

    default Exercise findByIdWithEagerExampleSubmissionsElseThrow(Long exerciseId) {
        return getValueElseThrow(findByIdWithEagerExampleSubmissions(exerciseId), exerciseId);
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
     * Finds all exercises stored in Artemis the user has participated in.
     * Currently only used for the data export
     *
     * @param userId the id of the user
     * @return a set of exercises the user has participated in with eager participations, submissions, results and feedbacks
     */
    // TODO: this query is way too big and complex, we MUST optimize it
    @Query("""
            SELECT e
            FROM Course c
                LEFT JOIN c.exercises e
                LEFT JOIN FETCH e.studentParticipations p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students students
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.student.id = :userId
                OR students.id = :userId
            """)
    Set<Exercise> getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksTestCasesByUserId(@Param("userId") long userId);

    /**
     * Finds all exercises filtered by feedback suggestion modules not null and due date.
     *
     * @param dueDate - filter by due date
     * @return Set of Exercises
     */
    Set<Exercise> findByFeedbackSuggestionModuleNotNullAndDueDateIsAfter(ZonedDateTime dueDate);

    /**
     * Find all exercises feedback suggestions (Athena) and with *Due Date* in the future.
     *
     * @return Set of Exercises
     */
    default Set<Exercise> findAllFeedbackSuggestionsEnabledExercisesWithFutureDueDate() {
        return findByFeedbackSuggestionModuleNotNullAndDueDateIsAfter(ZonedDateTime.now());
    }

    /**
     * Revokes the access by setting all exercises that currently utilize a restricted module to null.
     *
     * @param courseId                           The course for which the access should be revoked
     * @param restrictedFeedbackSuggestionModule Collection of restricted modules
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Exercise e
            SET e.feedbackSuggestionModule = NULL
            WHERE e.course.id = :courseId
                  AND e.feedbackSuggestionModule IN :restrictedFeedbackSuggestionModule
            """)
    void revokeAccessToRestrictedFeedbackSuggestionModulesByCourseId(@Param("courseId") Long courseId,
            @Param("restrictedFeedbackSuggestionModule") Collection<String> restrictedFeedbackSuggestionModule);

    /**
     * For an explanation, see {@link ExamResource#getAllExercisesWithPotentialPlagiarismForExam(long, long)}
     *
     * @param examId the id of the exam for which we want to get all exercises with potential plagiarism
     * @return a set of exercises with potential plagiarism
     */
    @Query("""
            SELECT e
            FROM Exercise e
                LEFT JOIN e.exerciseGroup eg
            WHERE eg IS NOT NULL
                AND eg.exam.id = :examId
                AND TYPE (e) IN (ModelingExercise, TextExercise, ProgrammingExercise)
            """)
    Set<Exercise> findAllExercisesWithPotentialPlagiarismByExamId(@Param("examId") long examId);

    @Query("""
            SELECT count(e) > 0
            FROM Exercise e
            WHERE e.id = :exerciseId
                AND e.exerciseGroup IS NOT NULL
            """)
    boolean isExamExercise(@Param("exerciseId") long exerciseId);

    /**
     * Returns a mapping from exercise type to count for a given course id. Note that there are way fewer courses
     * than exercise, so loading the course and joining the course is way faster than vice versa.
     *
     * @param courseId the courseId to get the exerciseType->count mapping for
     * @return a list of mappings from exercise type to count
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCountDTO(TYPE(e), COUNT(e))
            FROM Course c
                JOIN c.exercises e
            WHERE c.id = :courseId
                AND TYPE(e) IN (ModelingExercise, TextExercise, ProgrammingExercise, QuizExercise, FileUploadExercise)
            GROUP BY TYPE(e)
            """)
    List<ExerciseTypeCountDTO> countByCourseIdGroupedByType(@Param("courseId") long courseId);

    /**
     * Finds all exercises for a course with non-null due dates in the future,
     * including their categories and title.
     *
     * @param courseId the id of the course
     * @param now      the current date time
     * @return a set of exercises with future due dates and their categories
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
                AND e.dueDate IS NOT NULL
                AND e.dueDate > :now
                AND (e.releaseDate IS NULL OR e.releaseDate <= :now)
            """)
    Set<Exercise> findByCourseIdWithFutureDueDatesAndCategories(@Param("courseId") Long courseId, @Param("now") ZonedDateTime now);

    /**
     * Finds all exercises for a course with non-null due dates in the future,
     * including their categories and title.
     *
     * @param courseId the id of the course
     * @return a set of exercises with future due dates and their categories
     */
    default Set<Exercise> findByCourseIdWithFutureDueDatesAndCategories(Long courseId) {
        return findByCourseIdWithFutureDueDatesAndCategories(courseId, ZonedDateTime.now());
    }

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.calendar.NonQuizExerciseCalendarEventDTO(
                exercise.id,
                CASE TYPE(exercise)
                    WHEN FileUploadExercise THEN de.tum.cit.aet.artemis.core.util.CalendarEventType.FILE_UPLOAD_EXERCISE
                    WHEN TextExercise THEN de.tum.cit.aet.artemis.core.util.CalendarEventType.TEXT_EXERCISE
                    WHEN ModelingExercise THEN de.tum.cit.aet.artemis.core.util.CalendarEventType.MODELING_EXERCISE
                    ELSE de.tum.cit.aet.artemis.core.util.CalendarEventType.PROGRAMMING_EXERCISE
                END,
                exercise.title,
                exercise.releaseDate,
                exercise.startDate,
                exercise.dueDate,
                exercise.assessmentDueDate
            )
            FROM Exercise exercise
            WHERE exercise.course.id = :courseId AND TYPE(exercise) IN (FileUploadExercise, TextExercise, ModelingExercise, ProgrammingExercise)
            """)
    Set<NonQuizExerciseCalendarEventDTO> getNonQuizExerciseCalendarEventsDTOsForCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT NEW de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO(e.id, TYPE(e), e.includedInOverallScore, e.assessmentType, e.dueDate, e.assessmentDueDate, p.buildAndTestStudentSubmissionsAfterDueDate, e.maxPoints, e.bonusPoints, e.course.id)
            FROM Exercise e
                LEFT JOIN ProgrammingExercise p ON e.id = p.id
            WHERE e.course.id = :courseId
            """)
    Set<ExerciseCourseScoreDTO> findCourseExerciseScoreInformationByCourseId(@Param("courseId") long courseId);
}
