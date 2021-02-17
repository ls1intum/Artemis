package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

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
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentParticipationRepository extends JpaRepository<StudentParticipation, Long> {

    List<StudentParticipation> findByExerciseId(@Param("exerciseId") Long exerciseId);

    boolean existsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results r where participation.exercise.course.id = :#{#courseId} and (r.rated is null or r.rated = true)")
    List<StudentParticipation> findByCourseIdWithEagerRatedResults(@Param("courseId") Long courseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions s left join fetch s.results r where participation.testRun = false and participation.exercise.exerciseGroup.exam.id = :#{#examId} and r.rated = true")
    List<StudentParticipation> findByExamIdWithEagerSubmissionsRatedResults(@Param("examId") Long examId);

    @Query("select distinct participation from StudentParticipation participation where participation.exercise.course.id = :#{#courseId} and participation.team.shortName = :#{#teamShortName}")
    List<StudentParticipation> findAllByCourseIdAndTeamShortName(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);

    List<StudentParticipation> findByTeamId(Long teamId);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndStudentLogin(Long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = "results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @EntityGraph(type = LOAD, attributePaths = "submissions")
    Optional<StudentParticipation> findWithEagerSubmissionsByExerciseIdAndStudentLogin(Long exerciseId, String username);

    @EntityGraph(type = LOAD, attributePaths = "submissions")
    Optional<StudentParticipation> findWithEagerSubmissionsByExerciseIdAndTeamId(Long exerciseId, Long teamId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions s left join fetch s.results where participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findByExerciseIdWithEagerSubmissionsResult(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions s left join fetch s.results r left join fetch r.assessor where participation.exercise.id = :#{#exerciseId}")
    List<StudentParticipation> findByExerciseIdWithEagerSubmissionsResultAssessor(@Param("exerciseId") Long exerciseId);

    @Query("""
            select distinct participation from StudentParticipation participation
            left join fetch participation.submissions s
            left join fetch s.results r
            left join fetch r.assessor
            where participation.exercise.id = :#{#exerciseId}
            and participation.testRun = false
            """)
    List<StudentParticipation> findByExerciseIdWithEagerSubmissionsResultAssessorIgnoreTestRuns(@Param("exerciseId") Long exerciseId);

    /**
     * Get all participations for an exercise with each latest result (determined by id).
     * If there is no latest result (= no result at all), the participation will still be included in the returned ResultSet, but will have an empty Result array.
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results result where participation.exercise.id = :#{#exerciseId} and (result.id = (select max(id) from participation.results) or result is null)")
    List<StudentParticipation> findByExerciseIdWithLatestResult(@Param("exerciseId") Long exerciseId);

    @Query("""
            select distinct participation from StudentParticipation participation
            left join fetch participation.results result
            where participation.exercise.id = :#{#exerciseId}
            and participation.testRun = false
            and (result.id =
                (select max(id) from participation.results) or result is null)
            """)
    List<StudentParticipation> findByExerciseIdWithLatestResultIgnoreTestRunSubmissions(@Param("exerciseId") Long exerciseId);

    /**
     * Get all participations for an exercise with each latest {@link AssessmentType#AUTOMATIC} result and feedbacks (determined by id).
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results result left join fetch result.feedbacks where participation.exercise.id = :#{#exerciseId} and (result.id = (select max(prs.id) from participation.results prs where prs.assessmentType = 'AUTOMATIC'))")
    List<StudentParticipation> findByExerciseIdWithLatestAutomaticResultAndFeedbacks(@Param("exerciseId") Long exerciseId);

    // Manual result can either be from type MANUAL or SEMI_AUTOMATIC
    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results result left join fetch result.feedbacks where participation.exercise.id = :#{#exerciseId} and (result.assessmentType = 'MANUAL' or result.assessmentType = 'SEMI_AUTOMATIC')")
    List<StudentParticipation> findByExerciseIdWithManualResultAndFeedbacks(@Param("exerciseId") Long exerciseId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}")
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerSubmissions(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            select distinct participation
            from StudentParticipation participation
            where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}""")
    List<StudentParticipation> findByExerciseIdAndStudentId(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId}")
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.team.id = :#{#teamId}")
    List<StudentParticipation> findByExerciseIdAndTeamIdWithEagerSubmissions(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            SELECT DISTINCT participation
            FROM StudentParticipation participation
            WHERE participation.exercise.id = :#{#exerciseId} AND participation.team.id = :#{#teamId}
            """)
    List<StudentParticipation> findByExerciseIdAndTeamId(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results left join fetch participation.submissions where participation.exercise.id = :#{#exerciseId} and participation.team.id = :#{#teamId}")
    List<StudentParticipation> findByExerciseIdAndTeamIdWithEagerResultsAndSubmissions(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results as par left join fetch par.feedbacks left join fetch par.submission where participation.exercise.id = :#{#exerciseId} and participation.student.id = :#{#studentId} and (par.id = (select max(id) from participation.results) or par.id = null)")
    Optional<StudentParticipation> findByExerciseIdAndStudentIdWithLatestResult(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("select distinct participation from StudentParticipation participation left join fetch participation.results as par left join fetch par.feedbacks where participation.exercise.id = :#{#exerciseId} and participation.team.id = :#{#teamId} and (par.id = (select max(id) from participation.results) or par.id = null)")
    Optional<StudentParticipation> findByExerciseIdAndTeamIdWithLatestResult(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result and do not belong to test runs.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     *
     * If a student can have multiple submissions per exercise type, the latest submission (by id) will be returned.
     *
     * @param correctionRound the correction round the fetched results should belong to
     * @param exerciseId the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Query("""
            SELECT DISTINCT participation FROM StudentParticipation participation
            LEFT JOIN FETCH participation.submissions submission
            LEFT JOIN FETCH submission.results result
            LEFT JOIN FETCH result.feedbacks feedbacks
            LEFT JOIN FETCH result.assessor
            WHERE participation.exercise.id = :#{#exerciseId}
            AND participation.testRun = FALSE
            AND 0L = (SELECT COUNT(r2)
                             FROM Result r2 where r2.assessor IS NOT NULL
                                 AND (r2.rated IS NULL OR r2.rated = FALSE)
                                 AND r2.submission = submission)
            AND
              :#{#correctionRound} = (SELECT COUNT(r)
                             FROM Result r where r.assessor IS NOT NULL
                                 AND r.rated = TRUE
                                 AND r.submission = submission
                                 AND r.completionDate IS NOT NULL
                                 AND r.assessmentType IN ('MANUAL', 'SEMI_AUTOMATIC')
                                 AND (participation.exercise.dueDate IS NULL OR r.submission.submissionDate <= participation.exercise.dueDate))
            AND :#{#correctionRound} = (SELECT COUNT (prs)
                            FROM participation.results prs
                            WHERE prs.assessmentType IN ('MANUAL', 'SEMI_AUTOMATIC'))
            AND submission.submitted = true
            AND submission.id = (SELECT max(id) FROM participation.submissions)
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation(@Param("exerciseId") Long exerciseId,
            @Param("correctionRound") long correctionRound);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for the
     * corresponding submission yet.
     *
     * If a student can have multiple submissions per exercise type, the latest submission (by id) will be returned.
     *
     * @param exerciseId the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    @Query("""
            select distinct participation from Participation participation
            left join fetch participation.submissions submission
            left join fetch submission.results result
            left join fetch result.feedbacks feedbacks
            where participation.exercise.id = :#{#exerciseId}
            and not exists (select prs from participation.results prs
                where prs.assessmentType in ('MANUAL', 'SEMI_AUTOMATIC')) and submission.submitted = true and
                submission.id = (select max(id) from participation.submissions)
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResults(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<StudentParticipation> findWithEagerSubmissionsById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    Optional<StudentParticipation> findWithEagerResultsById(@Param("participationId") Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks" })
    Optional<StudentParticipation> findWithEagerResultsAndFeedbackById(@Param("participationId") Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database. Returns an empty Optional if the
     * participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions and results or an empty Optional
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "results", "team.students" })
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsById(Long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database.
     * Further, load the exercise and its course. Returns an empty Optional if the participation could not be found.
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions, results, exercise and course or an empty Optional
     */
    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "submissions.results.feedbacks", "results", "team.students" })
    Optional<StudentParticipation> findWithEagerSubmissionsResultsFeedbacksById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "results", "results.assessor" })
    Optional<StudentParticipation> findWithEagerSubmissionsAndResultsAssessorsById(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "submissions.results.assessor" })
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(long exerciseId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor a
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = FALSE
            """)
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "results" })
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsByExerciseId(long exerciseId);

    @Query("select distinct p from StudentParticipation p where p.student.id = :#{#studentId} and p.exercise in :#{#exercises}")
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

    @Query("select distinct p from StudentParticipation p left join fetch p.submissions s left join fetch s.results r where p.testRun = true and p.student.id = :#{#studentId} and p.exercise in :#{#exercises}")
    List<StudentParticipation> findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(@Param("studentId") Long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("select distinct p from StudentParticipation p left join fetch p.submissions s left join fetch s.results r left join fetch p.team t left join fetch t.students teamStudent where teamStudent.id = :#{#studentId} and p.exercise in :#{#exercises}")
    List<StudentParticipation> findByStudentIdAndTeamExercisesWithEagerSubmissionsResult(@Param("studentId") Long studentId, @Param("exercises") List<Exercise> exercises);

    @Query("select distinct p from StudentParticipation p left join fetch p.submissions s left join fetch s.results r left join fetch p.team t where p.exercise.course.id = :#{#courseId} and t.shortName = :#{#teamShortName}")
    List<StudentParticipation> findAllByCourseIdAndTeamShortNameWithEagerSubmissionsResult(@Param("courseId") Long courseId, @Param("teamShortName") String teamShortName);

    @EntityGraph(type = LOAD, attributePaths = { "submissions.results.assessor" })
    @Query("select distinct p from StudentParticipation p left join fetch p.submissions s left join fetch s.results r where p.exercise.id = :#{#exerciseId} and (r.assessor.id = :#{#assessorId} and s.id = (select max(id) from p.submissions) or s.id = null)")
    List<StudentParticipation> findWithLatestSubmissionByExerciseAndAssessor(@Param("exerciseId") Long exerciseId, @Param("assessorId") Long assessorId);

    @Query("""
            SELECT DISTINCT p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = FALSE
            AND (r.assessor.id = :#{#assessorId}
                AND s.id = (SELECT MAX(id) FROM p.submissions)
                OR s.id = NULL)
            AND 0L = :#{#correctionRound}
                """)
    List<StudentParticipation> findWithLatestSubmissionByExerciseAndAssessorAndCorrectionRoundIgnoreTestRuns(@Param("exerciseId") Long exerciseId,
            @Param("assessorId") Long assessorId, @Param("correctionRound") long correctionRound);

    /**
     * Count the number of submissions for each participation in a given exercise.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("select participation.id, count(submissions) from StudentParticipation participation left join participation.submissions submissions where participation.exercise.id = :#{#exerciseId} group by participation.id")
    List<long[]> countSubmissionsPerParticipationByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Count the number of submissions for each participation for a given team in a course
     *
     * @param courseId the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("select participation.id, count(submissions) from StudentParticipation participation left join participation.submissions submissions where participation.team.shortName = :#{#teamShortName} and participation.exercise.course.id = :#{#courseId} group by participation.id")
    List<long[]> countSubmissionsPerParticipationByCourseIdAndTeamShortName(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    @Query("""
            SELECT distinct p FROM StudentParticipation p
            LEFT JOIN FETCH p.submissions s
            LEFT JOIN FETCH s.results r
                WHERE p.exercise.id = :#{#exerciseId}
                AND p.testRun = FALSE
                AND s.id = (SELECT max(id) FROM p.submissions)
                AND EXISTS (SELECT s FROM Submission s
                    WHERE s.participation.id = p.id
                    AND s.submitted = TRUE
                    AND (r.assessor = :#{#assessor} OR r.assessor.id = NULL))
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
    default StudentParticipation findByIdWithSubmissionsResultsFeedbackElseThrow(long participationId) {
        return findWithEagerSubmissionsResultsFeedbacksById(participationId).orElseThrow(() -> new EntityNotFoundException("StudentParticipation", participationId));
    }

    @NotNull
    default StudentParticipation findByIdWithSubmissionsElseThrow(long participationId) {
        return findWithEagerSubmissionsById(participationId).orElseThrow(() -> new EntityNotFoundException("Participation", participationId));
    }

    /**
     * Get all participations belonging to exam with submissions and their relevant results.
     *
     * @param examId the id of the exam
     * @return list of participations belonging to course
     */
    default List<StudentParticipation> findByExamIdWithSubmissionRelevantResult(Long examId) {
        var participations = findByExamIdWithEagerSubmissionsRatedResults(examId); // without test run participations
        // filter out the participations of test runs which can only be made by instructors
        participations = participations.stream().filter(studentParticipation -> !studentParticipation.isTestRun()).collect(Collectors.toList());
        return filterParticipationsWithRelevantResults(participations, true);
    }

    /**
     * Get all participations belonging to course with relevant results.
     *
     * @param courseId the id of the course
     * @return list of participations belonging to course
     */
    default List<StudentParticipation> findByCourseIdWithRelevantResult(Long courseId) {
        List<StudentParticipation> participations = findByCourseIdWithEagerRatedResults(courseId);
        return filterParticipationsWithRelevantResults(participations, false);
    }

    /**
     * filters the relevant results by removing all irrelevant ones
     * @param participations the participations to get filtered
     * @param resultInSubmission flag to indicate if the results are represented in the submission or participation
     * @return the filtered participations
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
                }).collect(Collectors.toList());
    }

    /**
     * Get all participations for the given studentExam and exercises combined with their submissions with a result.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam studentExam with exercises loaded
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissionsResult(StudentExam studentExam) {
        if (studentExam.isTestRun()) {
            return findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(studentExam.getUser().getId(), studentExam.getExercises());
        }
        else {
            return findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
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
     * @param courseId the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return the number of submissions per participation in the given course for the team
     */
    default Map<Long, Integer> countSubmissionsPerParticipationByCourseIdAndTeamShortNameAsMap(long courseId, String teamShortName) {
        return convertListOfCountsIntoMap(countSubmissionsPerParticipationByCourseIdAndTeamShortName(courseId, teamShortName));
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
}
