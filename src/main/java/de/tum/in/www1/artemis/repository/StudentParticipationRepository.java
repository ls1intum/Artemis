package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmittedAnswerCount;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentParticipationRepository extends JpaRepository<StudentParticipation, Long> {

    Set<StudentParticipation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    boolean existsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p LEFT JOIN FETCH p.results r
            WHERE p.exercise.course.id = :#{#courseId}
                AND (r.rated IS NULL OR r.rated = true)
            """)
    List<StudentParticipation> findByCourseIdWithEagerRatedResults(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.results r
            LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND (p.student.id = :studentId OR ts.id = :studentId)
                AND (r.rated IS NULL OR r.rated = true)
            """)
    List<StudentParticipation> findByCourseIdAndStudentIdWithEagerRatedResults(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.testRun = false
                AND p.exercise.exerciseGroup.exam.id = :#{#examId}
                AND r.rated = true
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExamIdWithEagerLegalSubmissionsRatedResults(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            WHERE p.exercise.course.id = :#{#courseId}
            AND p.team.shortName = :#{#teamShortName}
            """)
    List<StudentParticipation> findAllByCourseIdAndTeamShortName(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);

    List<StudentParticipation> findByTeamId(Long teamId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndStudentLoginAndTestRun(Long exerciseId, String username, boolean testRun);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.student.login = :#{#username}
            """)
    Optional<StudentParticipation> findByExerciseIdAndStudentLogin(@Param("exerciseId") Long exerciseId, @Param("username") String username);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.student.login = :#{#username}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(@Param("exerciseId") Long exerciseId, @Param("username") String username);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.student.login = :#{#username}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
                AND p.testRun = :#{#testRun}
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(@Param("exerciseId") Long exerciseId, @Param("username") String username,
            @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            where p.exercise.id = :#{#exerciseId}
                AND p.team.id = :#{#teamId}
            """)
    Optional<StudentParticipation> findOneByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            where p.exercise.id = :#{#exerciseId}
                AND p.team.id = :#{#teamId}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = :#{#testRun}
            AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(@Param("exerciseId") Long exerciseId, @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = :#{#testRun}
            """)
    List<StudentParticipation> findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(@Param("exerciseId") Long exerciseId, @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor
            LEFT JOIN FETCH r.feedbacks
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = :#{#testRun}
            """)
    List<StudentParticipation> findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessorFeedbacks(@Param("exerciseId") Long exerciseId, @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.feedbacks
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.student.id = :#{#studentId}
            AND p.testRun = :#{#testRun}
            """)
    Optional<StudentParticipation> findByExerciseIdAndStudentIdAndTestRunWithEagerSubmissionsResultsFeedbacks(@Param("exerciseId") Long exerciseId,
            @Param("studentId") Long studentId, @Param("testRun") boolean testRun);

    default StudentParticipation findByExerciseIdAndStudentIdAndTestRunWithEagerSubmissionsResultsFeedbacksElseThrow(Long exerciseId, Long studentId, boolean testRun) {
        return findByExerciseIdAndStudentIdAndTestRunWithEagerSubmissionsResultsFeedbacks(exerciseId, studentId, testRun)
                .orElseThrow(() -> new EntityNotFoundException("Student Participation", exerciseId + "-" + studentId));
    }

    /**
     * Get all participations for an exercise with each latest result (determined by id).
     * If there is no latest result (= no result at all), the participation will still be included in the returned ResultSet, but will have an empty Result array.
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.results r
            LEFT JOIN FETCH r.submission s
            WHERE p.exercise.id = :#{#exerciseId}
                AND (r.id = (SELECT max(id) FROM p.results)
                    OR r IS NULL)
            """)
    Set<StudentParticipation> findByExerciseIdWithLatestResult(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.results r
            LEFT JOIN FETCH r.submission s
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = :#{#testRun}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
                AND r.id = (
                    SELECT max(id)
                    FROM p.results
                    WHERE completionDate IS NOT NULL
                    )
            """)
    Set<StudentParticipation> findByExerciseIdAndTestRunWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(@Param("exerciseId") Long exerciseId,
            @Param("testRun") boolean testRun);

    /**
     * Get all participations for an exercise with each latest {@link AssessmentType#AUTOMATIC} result and feedbacks (determined by id).
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.feedbacks
            left join fetch r.submission s
            where p.exercise.id = :#{#exerciseId}
                and (r.id = (select max(pr.id) from p.results pr
                    left join pr.submission prs
                    where pr.assessmentType = 'AUTOMATIC' and (prs.type <> 'ILLEGAL' or prs.type is null)))
            """)
    List<StudentParticipation> findByExerciseIdWithLatestAutomaticResultAndFeedbacks(@Param("exerciseId") Long exerciseId);

    /**
     * Get all participations without individual due date for an exercise with each latest {@link AssessmentType#AUTOMATIC} result and feedbacks (determined by id).
     *
     * @param exerciseId Exercise id.
     * @return participations for the exercise.
     */
    default List<StudentParticipation> findByExerciseIdWithLatestAutomaticResultAndFeedbacksWithoutIndividualDueDate(Long exerciseId) {
        return findByExerciseIdWithLatestAutomaticResultAndFeedbacks(exerciseId).stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
    }

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.feedbacks
            left join fetch r.submission s
            where p.id = :#{#participationId}
                and (r.id = (select max(pr.id) from p.results pr
                    left join pr.submission prs
                    where pr.assessmentType = 'AUTOMATIC' and (prs.type <> 'ILLEGAL' or prs.type is null)))
            """)
    Optional<StudentParticipation> findByIdWithLatestAutomaticResultAndFeedbacks(@Param("participationId") Long participationId);

    // Manual result can either be from type MANUAL or SEMI_AUTOMATIC
    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.feedbacks
            left join fetch r.submission s
            where p.exercise.id = :#{#exerciseId}
                 and (s.type <> 'ILLEGAL' or s.type is null)
                 and (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC')
            """)
    List<StudentParticipation> findByExerciseIdWithManualResultAndFeedbacks(@Param("exerciseId") Long exerciseId);

    default List<StudentParticipation> findByExerciseIdWithManualResultAndFeedbacksWithoutIndividualDueDate(Long exerciseId) {
        return findByExerciseIdWithManualResultAndFeedbacks(exerciseId).stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
    }

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.feedbacks
            left join fetch r.submission s
            where p.id = :#{#participationId}
                 and (s.type <> 'ILLEGAL' or s.type is null)
                 and (r.assessmentType = 'MANUAL' or r.assessmentType = 'SEMI_AUTOMATIC')
            """)
    Optional<StudentParticipation> findByIdWithManualResultAndFeedbacks(@Param("participationId") Long participationId);

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.submissions s
            where p.exercise.id = :#{#exerciseId}
                and p.student.id = :#{#studentId}
                and (s.type <> 'ILLEGAL' or s.type is null)
            """)
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerLegalSubmissions(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
            WHERE p.exercise.id = :#{#exerciseId} and p.student.id = :#{#studentId}
            """)
    List<StudentParticipation> findByExerciseIdAndStudentId(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results
            left join fetch p.submissions s
            where p.exercise.id = :#{#exerciseId}
                and p.student.id = :#{#studentId}
                and (s.type <> 'ILLEGAL' or s.type is null)
             """)
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerResultsAndLegalSubmissions(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.submissions s
            where p.exercise.id = :#{#exerciseId}
                and p.team.id = :#{#teamId}
                and (s.type <> 'ILLEGAL' or s.type is null)
            """)
    List<StudentParticipation> findByExerciseIdAndTeamIdWithEagerLegalSubmissions(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                WHERE p.exercise.id = :#{#exerciseId} AND p.team.id = :#{#teamId}
            """)
    List<StudentParticipation> findAllByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.submission rs
            left join fetch p.submissions s
            where p.exercise.id = :#{#exerciseId}
                and p.team.id = :#{#teamId}
                and (s.type <> 'ILLEGAL' or s.type is null)
                and (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    List<StudentParticipation> findByExerciseIdAndTeamIdWithEagerResultsAndLegalSubmissions(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            select distinct p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.feedbacks
            left join fetch r.submission s
            where p.exercise.id = :#{#exerciseId}
                and p.student.id = :#{#studentId}
                and p.testRun = :#{#testRun}
                and (r.id = (select max(pr.id) from p.results pr
                        left join pr.submission prs
                        where (prs.type <> 'ILLEGAL' or prs.type is null))
                    or r.id = null)
            """)
    Optional<StudentParticipation> findByExerciseIdAndStudentIdAndTestRunWithLatestResult(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId,
            @Param("testRun") boolean testRun);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result and do not belong to test runs.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * <p>
     * If a student can have multiple submissions per exercise type, the latest not {@link de.tum.in.www1.artemis.domain.enumeration.SubmissionType#ILLEGAL} ILLEGAL submission (by id) will be returned.
     *
     * @param correctionRound the correction round the fetched results should belong to
     * @param exerciseId      the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Query("""
                SELECT DISTINCT p FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions submission
                LEFT JOIN FETCH submission.results result
                LEFT JOIN FETCH result.feedbacks feedbacks
                LEFT JOIN FETCH result.assessor
                WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = FALSE
                AND 0L = (SELECT COUNT(r2)
                                 FROM Result r2 WHERE r2.assessor IS NOT NULL
                                     AND (r2.rated IS NULL OR r2.rated = FALSE)
                                     AND r2.submission = submission)
                AND
                  :#{#correctionRound} = (SELECT COUNT(r)
                                 FROM Result r WHERE r.assessor IS NOT NULL
                                     AND r.rated = TRUE
                                     AND r.submission = submission
                                     AND r.completionDate IS NOT NULL
                                     AND r.assessmentType IN ('MANUAL', 'SEMI_AUTOMATIC')
                                     AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate))
                AND :#{#correctionRound} = (SELECT COUNT (prs)
                                FROM p.results prs
                                WHERE prs.assessmentType IN ('MANUAL', 'SEMI_AUTOMATIC'))
                AND submission.submitted = true
                AND submission.id = (SELECT max(id) FROM p.submissions)
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation(@Param("exerciseId") Long exerciseId,
            @Param("correctionRound") long correctionRound);

    @Query("""
            SELECT DISTINCT p FROM Participation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.feedbacks
            WHERE p.exercise.id = :#{#exerciseId}
            AND (p.individualDueDate IS NULL OR p.individualDueDate <= :#{#now})
            AND p.testRun = false
            AND NOT EXISTS
                (SELECT prs FROM p.results prs
                    WHERE prs.assessmentType IN ('MANUAL', 'SEMI_AUTOMATIC'))
                    AND s.submitted = true
                    AND s.id = (SELECT max(id) FROM p.submissions)
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResultsWithPassedIndividualDueDateIgnoreTestRuns(@Param("exerciseId") Long exerciseId,
            @Param("now") ZonedDateTime now);

    @Query("""
            select p from Participation p
            left join fetch p.submissions s
            where p.id = :#{#participationId} and (s.type <> 'ILLEGAL' or s.type is null)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsById(@Param("participationId") Long participationId);

    @Query("""
            select p from Participation p
            left join fetch p.results r
            left join fetch r.submission
            where p.id = :#{#participationId}
            """)
    Optional<StudentParticipation> findWithEagerResultsById(@Param("participationId") Long participationId);

    @Query("""
            select p from Participation p
            left join fetch p.results r
            left join fetch r.submission rs
            left join fetch r.feedbacks
            where p.id = :#{#participationId} and (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    Optional<StudentParticipation> findWithEagerResultsAndFeedbackById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions and results or an empty Optional
     */
    @Query("""
            select p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.submission rs
            left join fetch p.submissions s
            left join fetch s.results
            left join p.team.students
            where p.id = :#{#participationId}
                and (s.type <> 'ILLEGAL' or s.type is null)
                and (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsAndResultsById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database.
     * Further, load the exercise and its course. Returns an empty Optional if the participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions, results, exercise and course or an empty Optional
     */
    @Query("""
            select p from StudentParticipation p
            left join fetch p.results r
            left join fetch p.submissions s
            left join fetch s.results sr
            left join fetch sr.feedbacks
            left join p.team.students
            where p.id = :#{#participationId}
            """)
    Optional<StudentParticipation> findWithEagerSubmissionsResultsFeedbacksById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database.
     * Further, load the exercise and its course. Returns an empty Optional if the participation could not be found.
     * <p>
     * Note: Does NOT load illegal submissions!
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions, results, exercise and course or an empty Optional
     */
    @Query("""
            select p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.submission rs
            left join fetch p.submissions s
            left join fetch s.results sr
            left join fetch sr.feedbacks
            left join p.team.students
            where p.id = :#{#participationId}
                and (s.type <> 'ILLEGAL' or s.type is null)
                and (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsResultsFeedbacksById(@Param("participationId") Long participationId);

    @Query("""
            select p from StudentParticipation p
            left join fetch p.results r
            left join fetch r.submission rs
            left join fetch p.submissions s
            left join fetch r.assessor
            where p.id = :#{#participationId}
                and (s.type <> 'ILLEGAL' or s.type is null)
                and (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsAndResultsAssessorsById(@Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "submissions.results.assessor" })
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(long exerciseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor a
            WHERE p.exercise.id = :#{#exerciseId} AND p.testRun = FALSE
            """)
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    @Query(value = """
            SELECT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH p.results r
            WHERE p.exercise.id = :#{#exerciseId}
                  AND (p.student.firstName LIKE %:partialStudentName% OR p.student.lastName LIKE %:partialStudentName%)
                  AND r.completionDate IS NOT NULL
            """, countQuery = """
            SELECT count(p) FROM StudentParticipation p
            LEFT JOIN p.submissions s
            LEFT JOIN p.results r
            WHERE p.exercise.id = :#{#exerciseId}
                  AND (p.student.firstName LIKE %:partialStudentName% OR p.student.lastName LIKE %:partialStudentName%)
                  AND r.completionDate IS NOT NULL
            """)
    Page<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("partialStudentName") String partialStudentName,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.results r
            LEFT JOIN FETCH r.submission rs
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results sr
            WHERE p.exercise.id = :#{#exerciseId}
                AND (s.type <> 'ILLEGAL' or s.type is null)
                AND (rs.type <> 'ILLEGAL' or rs.type is null)
            """)
    List<StudentParticipation> findAllWithEagerLegalSubmissionsAndEagerResultsByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            WHERE p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercises(@Param("studentId") Long studentId, @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.testRun = FALSE
                AND p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.testRun = FALSE
                AND p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsIgnoreTestRuns(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor
            WHERE p.testRun = FALSE
                AND p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.testRun = true
                AND p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            WHERE p.testRun = true
                AND p.student.id = :#{#studentId}
                AND p.exercise in :#{#exercises}
            """)
    List<StudentParticipation> findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissions(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
                SELECT DISTINCT p FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students teamStudent
                    WHERE teamStudent.id = :#{#studentId}
                    AND p.exercise in :#{#exercises}
                    AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    List<StudentParticipation> findByStudentIdAndTeamExercisesWithEagerLegalSubmissionsResult(@Param("studentId") Long studentId, @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH p.team t
            WHERE p.exercise.course.id = :#{#courseId}
                AND t.shortName = :#{#teamShortName}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            """)
    List<StudentParticipation> findAllByCourseIdAndTeamShortNameWithEagerLegalSubmissionsResult(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);

    /**
     * Count the number of submissions for each participation in a given exercise.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("""
            SELECT p.id, COUNT(s) FROM StudentParticipation p
            LEFT JOIN p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
            GROUP BY p.id
            """)
    List<long[]> countSubmissionsPerParticipationByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Count the number of submissions for each participation for a given team in a course
     *
     * @param courseId      the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("""
            SELECT p.id, COUNT(s) FROM StudentParticipation p
            LEFT JOIN p.submissions s
            WHERE p.team.shortName = :#{#teamShortName}
                AND p.exercise.course.id = :#{#courseId}
                AND (s.type <> 'ILLEGAL' OR s.type IS NULL)
            GROUP BY p.id
            """)
    List<long[]> countLegalSubmissionsPerParticipationByCourseIdAndTeamShortName(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    // TODO SE Improve - maybe leave out max id line?
    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = FALSE
                AND s.id = (SELECT max(id) FROM p.submissions)
                AND EXISTS (SELECT s1 FROM p.submissions s1
                    WHERE s1.participation.id = p.id
                    AND s1.submitted = TRUE
                    AND (r.assessor = :#{#assessor} OR r.assessor.id IS NULL))
            """)
    List<StudentParticipation> findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(@Param("exerciseId") Long exerciseId,
            @Param("assessor") User assessor);

    @NotNull
    default StudentParticipation findByIdElseThrow(long studentParticipationId) {
        return findById(studentParticipationId).orElseThrow(() -> new EntityNotFoundException("Student Participation", studentParticipationId));
    }

    @NotNull
    default StudentParticipation findByIdWithResultsElseThrow(long participationId) {
        return findWithEagerResultsById(participationId).orElseThrow(() -> new EntityNotFoundException("StudentParticipation", participationId));
    }

    @NotNull
    default StudentParticipation findByIdWithLegalSubmissionsResultsFeedbackElseThrow(long participationId) {
        return findWithEagerLegalSubmissionsResultsFeedbacksById(participationId).orElseThrow(() -> new EntityNotFoundException("StudentParticipation", participationId));
    }

    @NotNull
    default StudentParticipation findByIdWithLegalSubmissionsElseThrow(long participationId) {
        return findWithEagerLegalSubmissionsById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    /**
     * Get all participations belonging to exam with submissions and their relevant results.
     *
     * @param examId the id of the exam
     * @return an unmodifiable list of participations belonging to course
     */
    default List<StudentParticipation> findByExamIdWithSubmissionRelevantResult(Long examId) {
        var participations = findByExamIdWithEagerLegalSubmissionsRatedResults(examId); // without test run participations
        // filter out the participations of test runs which can only be made by instructors
        participations = participations.stream().filter(studentParticipation -> !studentParticipation.isTestRun()).toList();
        return filterParticipationsWithRelevantResults(participations, true);
    }

    /**
     * Get all participations belonging to course with relevant results.
     *
     * @param courseId the id of the course
     * @return an unmodifiable list of participations belonging to course
     */
    default List<StudentParticipation> findByCourseIdWithRelevantResult(Long courseId) {
        List<StudentParticipation> participations = findByCourseIdWithEagerRatedResults(courseId);
        return filterParticipationsWithRelevantResults(participations, false);
    }

    /**
     * filters the relevant results by removing all irrelevant ones
     *
     * @param participations     the participations to get filtered
     * @param resultInSubmission flag to indicate if the results are represented in the submission or participation
     * @return an unmodifiable list of filtered participations
     */
    private List<StudentParticipation> filterParticipationsWithRelevantResults(List<StudentParticipation> participations, boolean resultInSubmission) {

        return participations.stream()

                // Filter out participations without Students
                // These participations are used e.g. to store template and solution build plans in programming exercises
                .filter(participation -> participation.getParticipant() != null)

                // filter all irrelevant results, i.e. rated = false or no completion date or no score
                .peek(participation -> {
                    List<Result> relevantResults = new ArrayList<>();

                    // Get the results over the participation or over submissions
                    Set<Result> resultsOfParticipation;
                    if (resultInSubmission) {
                        resultsOfParticipation = participation.getSubmissions().stream().map(Submission::getLatestResult).collect(Collectors.toSet());
                    }
                    else {
                        resultsOfParticipation = participation.getResults();
                    }
                    // search for the relevant result by filtering out irrelevant results using the continue keyword
                    // this for loop is optimized for performance and thus not very easy to understand ;)
                    for (Result result : resultsOfParticipation) {
                        // this should not happen because the database call above only retrieves rated results
                        if (Boolean.FALSE.equals(result.isRated())) {
                            continue;
                        }
                        if (result.getCompletionDate() == null || result.getScore() == null) {
                            // we are only interested in results with completion date and with score
                            continue;
                        }
                        relevantResults.add(result);
                    }
                    // we take the last rated result
                    if (!relevantResults.isEmpty()) {
                        // make sure to take the latest result
                        relevantResults.sort((r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate()));
                        Result correctResult = relevantResults.get(0);
                        relevantResults.clear();
                        relevantResults.add(correctResult);
                    }
                    participation.setResults(new HashSet<>(relevantResults));
                }).toList();
    }

    /**
     * Get all participations for the given studentExam and exercises combined with their submissions with a result.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam  studentExam with exercises loaded
     * @param withAssessor (only for non-test runs) if assessor should be loaded with the result
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissionsResult(StudentExam studentExam, boolean withAssessor) {
        if (studentExam.isTestRun()) {
            return findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(studentExam.getUser().getId(), studentExam.getExercises());
        }
        else {
            if (withAssessor) {
                return findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
            }
            else {
                return findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
            }
        }
    }

    /**
     * Get all participations for the given studentExam and exercises combined with their submissions, but without results and assessors.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam  studentExam with exercises loaded
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissions(StudentExam studentExam) {
        if (studentExam.isTestRun()) {
            return findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissions(studentExam.getUser().getId(), studentExam.getExercises());
        }
        else {
            return findByStudentIdAndIndividualExercisesWithEagerSubmissionsIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
        }
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return the number of submissions per participation in the given exercise
     */
    default Map<Long, Integer> countSubmissionsPerParticipationByExerciseIdAsMap(long exerciseId) {
        return convertListOfCountsIntoMap(countSubmissionsPerParticipationByExerciseId(exerciseId));
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param courseId      the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return the number of submissions per participation in the given course for the team
     */
    default Map<Long, Integer> countLegalSubmissionsPerParticipationByCourseIdAndTeamShortNameAsMap(long courseId, String teamShortName) {
        return convertListOfCountsIntoMap(countLegalSubmissionsPerParticipationByCourseIdAndTeamShortName(courseId, teamShortName));
    }

    /**
     * Converts List<[participationId, submissionCount]> into Map<participationId -> submissionCount>
     *
     * @param participationIdAndSubmissionCountPairs list of pairs (participationId, submissionCount)
     * @return map of participation id to submission count
     */
    private static Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> participationIdAndSubmissionCountPairs) {
        return participationIdAndSubmissionCountPairs.stream().collect(Collectors.toMap(participationIdAndSubmissionCountPair -> participationIdAndSubmissionCountPair[0], // participationId
                participationIdAndSubmissionCountPair -> Math.toIntExact(participationIdAndSubmissionCountPair[1]) // submissionCount
        ));
    }

    @Query("""
            SELECT COUNT(p) FROM StudentParticipation p
                LEFT JOIN p.exercise exercise WHERE exercise.id = :#{#exerciseId}
            AND p.testRun = :#{#testRun}
            GROUP BY exercise.id
            """)
    Long countParticipationsByExerciseIdAndTestRun(@Param("exerciseId") Long exerciseId, @Param("testRun") boolean testRun);

    /**
     * Adds the transient property numberOfParticipations for each exercise to
     * let instructors know which exercise has how many participations
     *
     * @param exerciseGroup exercise group for which to add transient property
     */
    default void addNumberOfExamExerciseParticipations(ExerciseGroup exerciseGroup) {
        exerciseGroup.getExercises().forEach(exercise -> {
            Long numberOfParticipations = countParticipationsByExerciseIdAndTestRun(exercise.getId(), false);
            // avoid setting to null in case not participations exist
            exercise.setNumberOfParticipations((numberOfParticipations != null) ? numberOfParticipations : 0);
        });
    }

    /**
     * Gets all the participations of the user in the given exercises
     *
     * @param user      the user to get the participations for
     * @param exercises the exercise to get the participations for
     * @return an unmodifiable list of participations of the user in the exercises
     */
    default List<StudentParticipation> getAllParticipationsOfUserInExercises(User user, Set<Exercise> exercises) {
        Map<ExerciseMode, List<Exercise>> exercisesGroupedByExerciseMode = exercises.stream().collect(Collectors.groupingBy(Exercise::getMode));
        List<Exercise> individualExercises = Objects.requireNonNullElse(exercisesGroupedByExerciseMode.get(ExerciseMode.INDIVIDUAL), List.of());
        List<Exercise> teamExercises = Objects.requireNonNullElse(exercisesGroupedByExerciseMode.get(ExerciseMode.TEAM), List.of());

        if (individualExercises.isEmpty() && teamExercises.isEmpty()) {
            return List.of();
        }

        // Note: we need two database calls here, because of performance reasons: the entity structure for team is significantly different and a combined database call
        // would lead to a SQL statement that cannot be optimized

        // 1st: fetch participations, submissions and results for individual exercises
        var individualParticipations = findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(), individualExercises);

        // 2nd: fetch participations, submissions and results for team exercises
        var teamParticipations = findByStudentIdAndTeamExercisesWithEagerLegalSubmissionsResult(user.getId(), teamExercises);

        // 3rd: merge both into one list for further processing
        return Stream.concat(individualParticipations.stream(), teamParticipations.stream()).toList();
    }

    /**
     * Checks if the exercise has any test runs and sets the transient property if it does
     *
     * @param exercise - the exercise for which we check if test runs exist
     */
    default void checkTestRunsExist(Exercise exercise) {
        Long containsTestRunParticipations = countParticipationsByExerciseIdAndTestRun(exercise.getId(), true);
        if (containsTestRunParticipations != null && containsTestRunParticipations > 0) {
            exercise.setTestRunParticipationsExist(Boolean.TRUE);
        }
    }

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.quiz.QuizSubmittedAnswerCount(
                count(a.id),
                s.id,
                p.id
            )
            FROM
                SubmittedAnswer a
                LEFT JOIN a.submission s
                LEFT JOIN s.participation p
            WHERE
                p.exercise.exerciseGroup.exam.id = :examId
            GROUP BY s.id
            """)
    List<QuizSubmittedAnswerCount> findSubmittedAnswerCountForQuizzesInExam(@Param("examId") long examId);
}
