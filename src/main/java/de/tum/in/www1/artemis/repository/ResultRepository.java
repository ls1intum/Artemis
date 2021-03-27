package de.tum.in.www1.artemis.repository;

import static java.util.Arrays.asList;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessments;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    @Query("""
                    SELECT r
                    FROM Result r LEFT JOIN FETCH r.assessor
                    WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerAssessor(@Param("resultId") Long resultId);

    List<Result> findByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findAllByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findByParticipationExerciseIdOrderByCompletionDateAsc(Long exerciseId);

    // TODO: cleanup unused queries

    @Query("""
            select distinct r from Result r left join fetch r.feedbacks
            where r.completionDate =
                (select max(rr.completionDate) from Result rr
                    where rr.assessmentType = 'AUTOMATIC'
                    and rr.participation.exercise.id = :exerciseId
                    and rr.participation.student.id = r.participation.student.id)
                and r.participation.exercise.id = :exerciseId
                and r.participation.student.id IS NOT NULL
            order by r.completionDate asc
              """)
    List<Result> findLatestAutomaticResultsWithEagerFeedbacksForExercise(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    Optional<Result> findFirstByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    Optional<Result> findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(Long participationId, boolean rated);

    Optional<Result> findDistinctBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "assessor")
    Optional<Result> findDistinctWithAssessorBySubmissionId(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findDistinctWithFeedbackBySubmissionId(Long submissionId);

    @Query("select r from Result r left join fetch r.feedbacks where r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long id);

    @Query("select r from Result r left join fetch r.submission left join fetch r.feedbacks where r.id = :resultId")
    Optional<Result> findByIdWithEagerSubmissionAndFeedbacks(@Param("resultId") Long id);

    @Query("select r from Result r left join fetch r.feedbacks left join fetch r.assessor where r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacksAndAssessor(@Param("resultId") Long id);

    /**
     * Load a result from the database by its id together with the associated submission, the list of feedback items and the assessor.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission, feedback list and assessor, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results", "feedbacks", "assessor" })
    Optional<Result> findWithEagerSubmissionAndFeedbackAndAssessorById(Long resultId);

    /**
     * counts the number of assessments of a course, which are either rated or not rated
     *
     * @param courseId  - the id of the course
     * @param rated     - only counts assessments which are either rated or not rated
     * @return count of rated/unrated assessments of a course
     */
    @Query("""
            SELECT
                count(r)
            FROM
                Result r join r.participation p join p.exercise e join e.course c
            WHERE
                r.completionDate is not null
                and r.assessor is not null
                and r.rated = :#{#rated}
                and c.id = :#{#courseId}
            """)
    Long countAssessmentsByCourseIdAndRated(long courseId, boolean rated);

    List<Result> findAllByParticipation_Exercise_CourseId(Long courseId);

    /**
     * Load a result from the database by its id together with the associated submission and the list of feedback items.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission and feedback list, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findWithEagerSubmissionAndFeedbackById(long resultId);

    @Query("""
                SELECT COUNT(DISTINCT p) FROM StudentParticipation p left join p.results r
                WHERE p.exercise.id = :exerciseId
                AND r.assessor IS NOT NULL
                AND r.rated = TRUE
                AND r.completionDate IS NOT NULL
                AND (p.exercise.dueDate IS NULL
                    OR r.submission.submissionDate <= p.exercise.dueDate)
            """)
    long countNumberOfFinishedAssessmentsForExercise(@Param("exerciseId") Long exerciseId);

    /**
     * Gets the number of assessments with a rated result set by an assessor for an exercise
     *
     * @param exerciseId the exercise to get the number for
     * @return the number of assessments with a rated result set by an assessor
     */
    @Query("""
            SELECT COUNT(DISTINCT p.id) FROM ParticipantScore p
            WHERE p.exercise.id = :exerciseId
                AND p.lastResult IS NOT NULL
                AND p.lastResult.assessor IS NOT NULL
            """)
    long countNumberOfRatedResultsForExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT COUNT(DISTINCT p) FROM StudentParticipation p
            left join p.results r
            WHERE p.exercise.id = :exerciseId
            AND p.testRun = FALSE
            AND r.assessor IS NOT NULL
            AND r.rated = TRUE
            AND r.submission.submitted = TRUE
            AND r.completionDate IS NOT NULL
            AND (p.exercise.dueDate IS NULL
                OR r.submission.submissionDate <= p.exercise.dueDate)
            """)
    long countNumberOfFinishedAssessmentsForExerciseIgnoreTestRuns(@Param("exerciseId") Long exerciseId);

    /**
     * @param exerciseId id of exercise
     * @return a list that contains the count of manual assessments for each studentParticipation of the exercise
     */
    @Query("""
            SELECT COUNT(r.id)
            FROM StudentParticipation p JOIN p.submissions s JOIN s.results r
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND r.completionDate IS NOT NULL
                AND r.rated = TRUE
                AND r.assessor IS NOT NULL
                GROUP BY p.id
            """)
    List<Long> countNumberOfFinishedAssessmentsByExerciseIdIgnoreTestRuns(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT r
                FROM StudentParticipation p join p.submissions s join s.results r
                WHERE p.exercise.id = :exerciseId
                    AND p.testRun = FALSE
                    AND s.submitted = TRUE
                    AND r.completionDate IS NULL
                    AND r.assessor.id <> :tutorId
            """)
    List<Result> countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRoundsIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("tutorId") Long tutorId);

    /**
     * count the number of finsished assessments of an exam with given examId
     *
     * @param examId id of the exam
     * @return a list that contains the count of manual assessments for each studentParticipation of the exam
     */
    @Query("""
            SELECT COUNT(r.id)
            FROM StudentParticipation p JOIN p.submissions s JOIN s.results r
            WHERE p.exercise.exerciseGroup.exam.id = :examId
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND r.completionDate IS NOT NULL
                AND r.rated = TRUE
                AND r.assessor IS NOT NULL
                GROUP BY p.id
            """)
    List<Long> countNumberOfFinishedAssessmentsByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    List<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(Long exerciseId);

    @Query("""
            SELECT COUNT(DISTINCT p) FROM Participation p JOIN p.results r
            WHERE p.exercise.id = :exerciseId
                AND r.assessor IS NOT NULL
                AND r.assessmentType IN :types
                AND r.rated = TRUE
                AND r.completionDate IS NOT NULL
                AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)
              """)
    long countNumberOfAssessmentsByTypeForExerciseBeforeDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    @Query("""
            SELECT COUNT(DISTINCT p) FROM Participation p JOIN p.results r
            WHERE p.exercise.id = :exerciseId
                AND r.assessor IS NOT NULL
                AND r.assessmentType IN :types
                AND r.rated = FALSE
                AND r.completionDate IS NOT NULL
                AND p.exercise.dueDate IS NOT NULL
                AND r.submission.submissionDate > p.exercise.dueDate
            """)
    long countNumberOfAssessmentsByTypeForExerciseAfterDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    long countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(Long tutorId, Long exerciseId, boolean rated);

    @Query("""
            SELECT r
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id = :exerciseId
                AND p.student.id = :studentId
                AND r.score IS NOT NULL
                AND r.completionDate IS NOT NULL
                AND s.type <> 'ILLEGAL'
            ORDER BY p.id DESC, s.id DESC, r.id DESC
            """)
    List<Result> getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            SELECT r
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id = :exerciseId
                AND p.team.id = :teamId
                AND r.score IS NOT NULL
                AND r.completionDate IS NOT NULL
                AND s.type <> 'ILLEGAL'
            ORDER BY p.id DESC, s.id DESC, r.id DESC
            """)
    List<Result> getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @Query("""
            SELECT r
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id = :exerciseId
                AND p.student.id = :studentId
                AND r.score IS NOT NULL
                AND r.completionDate IS NOT NULL
                AND r.rated = true
                AND s.type <> 'ILLEGAL'
            ORDER BY p.id DESC, s.id DESC, r.id DESC
            """)
    List<Result> getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(@Param("exerciseId") Long exerciseId, @Param("studentId") Long studentId);

    @Query("""
            SELECT r
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id = :exerciseId
                AND p.team.id = :teamId
                AND r.score IS NOT NULL
                AND r.completionDate IS NOT NULL
                AND r.rated = true
                AND s.type <> 'ILLEGAL'
            ORDER BY p.id DESC, s.id DESC, r.id DESC
            """)
    List<Result> getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    /**
     * Checks if a result for the given participation exists.
     *
     * @param participationId the id of the participation to check.
     * @return true if a result for the given participation exists, false otherwise.
     */
    boolean existsByParticipationId(long participationId);

    /**
     * Returns true if there is at least one result for the given exercise.
     *
     * @param exerciseId id of an Exercise.
     * @return true if there is a result, false if not.
     */
    boolean existsByParticipation_ExerciseId(long exerciseId);

    /**
     * Use this method only for exams!
     * Given an exerciseId and the number of correctionRounds, return the number of assessments that have been finished, for that exerciseId and each correctionRound
     *
     * @param exercise  - the exercise we are interested in
     * @param numberOfCorrectionRounds - the correction round we want finished assessments for
     * @return an array of the number of assessments for the exercise for a given correction round
     */
    default DueDateStat[] countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(Exercise exercise, int numberOfCorrectionRounds) {

        // here we receive a list which contains an entry for each studentparticipation of the exercise.
        // the entry simply is the number of already created and submitted manual results, so the number is either 1 or 2
        List<Long> countlist = countNumberOfFinishedAssessmentsByExerciseIdIgnoreTestRuns(exercise.getId());
        return convertDatabaseResponseToDueDateStats(countlist, numberOfCorrectionRounds);
    }

    /**
     * Use this method only for exams!
     * Given an exerciseId and the number of correctionRounds, return the number of assessments that have been finished, for that exerciseId and each correctionRound
     *
     * @param exercise  - the exercise we are interested in
     * @param numberOfCorrectionRounds - the correction round we want finished assessments for
     * @param tutor tutor for which we want to coutnt the
     * @return an array of the number of assessments for the exercise for a given correction round
     */
    default DueDateStat[] countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(Exercise exercise, int numberOfCorrectionRounds, User tutor) {
        if (exercise.isExamExercise()) {
            DueDateStat[] correctionRoundsDataStats = new DueDateStat[numberOfCorrectionRounds];
            var resultsLockedByOtherTutors = countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRoundsIgnoreTestRuns(exercise.getId(), tutor.getId());

            correctionRoundsDataStats[0] = new DueDateStat(resultsLockedByOtherTutors.stream().filter(result -> result.isRated() == null).count(), 0L);
            // so far the number of correctionRounds is limited to 2
            if (numberOfCorrectionRounds == 2) {
                correctionRoundsDataStats[1] = new DueDateStat(resultsLockedByOtherTutors.stream().filter(result -> result.isRated() != null).count(), 0L);
            }
            return correctionRoundsDataStats;
        }
        return null;
    }

    /**
     * Use this method only for exams!
     * Given an exerciseId and the number of correctionRounds, return the number of assessments that have been finished, for that exerciseId and each correctionRound
     *
     * @param examId   - the id of the exam we are interested in
     * @param numberOfCorrectionRounds - the correction round we want finished assessments for
     * @return an array of the number of assessments for the exercise for a given correction round
     */
    default DueDateStat[] countNumberOfFinishedAssessmentsForExamForCorrectionRounds(Long examId, int numberOfCorrectionRounds) {

        // here we receive a list which contains an entry for each studentparticipation of the exam.
        // the entry simply is the number of already created and submitted manual results, so the number is either 1 or 2
        List<Long> countlist = countNumberOfFinishedAssessmentsByExamIdIgnoreTestRuns(examId);
        return convertDatabaseResponseToDueDateStats(countlist, numberOfCorrectionRounds);
    }

    /**
     * Takes the Long List database response and converts it to the according DueDateStats
     *
     * @param countlist                 - the lists returned from the database
     * @param numberOfCorrectionRounds  - numbmer of the correction rounds which is set for the given exam
     * @return an array of DueDateStats which contains a DueDateStat with the number of assessments for each correction round.
     */
    default DueDateStat[] convertDatabaseResponseToDueDateStats(List<Long> countlist, int numberOfCorrectionRounds) {
        DueDateStat[] correctionRoundsDataStats = new DueDateStat[numberOfCorrectionRounds];

        // depending on the number of correctionRounds we create 1 or 2 DueDateStats that contain the sum of all participations:
        // with either 1 or more manual results, OR 2 or more manual results
        correctionRoundsDataStats[0] = new DueDateStat(countlist.stream().filter(x -> x >= 1L).count(), 0L);
        // so far the number of correctionRounds is limited to 2
        if (numberOfCorrectionRounds == 2) {
            correctionRoundsDataStats[1] = new DueDateStat(countlist.stream().filter(x -> x >= 2L).count(), 0L);
        }
        return correctionRoundsDataStats;
    }

    /**
     * Calculate the number of assessments which are either AUTOMATIC or SEMI_AUTOMATIC for a given exercise
     *
     * @param exerciseId the exercise we are interested in
     * @return number of assessments for the exercise
     */
    default DueDateStat countNumberOfAutomaticAssistedAssessmentsForExercise(Long exerciseId) {
        return new DueDateStat(countNumberOfAssessmentsByTypeForExerciseBeforeDueDate(exerciseId, asList(AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC)),
                countNumberOfAssessmentsByTypeForExerciseAfterDueDate(exerciseId, asList(AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC)));
    }

    /**
     * Given an exerciseId, return the number of assessments for that exerciseId that have been completed (e.g. no draft!)
     *
     * @param exerciseId - the exercise we are interested in
     * @param examMode should be used for exam exercises to ignore test run submissions
     * @return a number of assessments for the exercise
     */
    default DueDateStat countNumberOfFinishedAssessmentsForExercise(Long exerciseId, boolean examMode) {
        if (examMode) {
            return new DueDateStat(countNumberOfFinishedAssessmentsForExerciseIgnoreTestRuns(exerciseId), 0L);
        }
        return new DueDateStat(countNumberOfFinishedAssessmentsForExercise(exerciseId), 0L);
    }

    /**
     * Given a courseId, return the number of assessments for that course that have been completed (e.g. no draft!)
     *
     * !! this is very slow - 3787 ms TODO improve
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    default DueDateStat countNumberOfAssessments(Long courseId) {
        return new DueDateStat(countAssessmentsByCourseIdAndRated(courseId, true), countAssessmentsByCourseIdAndRated(courseId, false));
    }

    /**
     * Given a courseId, return the number of assessments for that course that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    default DueDateStat countNumberOfAssessmentsOfExam(Long courseId) {
        return new DueDateStat(countAssessmentsByCourseIdAndRated(courseId, true), 0);
    }

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessments(
                r.assessor.id,
                count(r),
                sum(e.maxPoints)
                )
            FROM
                Result r join r.participation p join p.exercise e join e.course c join r.assessor a
            WHERE
                r.completionDate is not null
                and c.id = :#{#courseId}
            GROUP BY a.id
            """)
    List<TutorLeaderboardAssessments> findTutorLeaderboardAssessmentByCourseId(@Param("courseId") long courseId);

    // Alternative which might be faster, in particular for complaints in the other repositories

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessments(
                a.id,
                count(r),
                sum(e.maxPoints)
            )
            FROM
                Result r join r.participation p join p.exercise e join r.assessor a
            WHERE
                r.completionDate is not null
                and e.id = :#{#exerciseId}
            GROUP BY a.id
            """)
    List<TutorLeaderboardAssessments> findTutorLeaderboardAssessmentByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT
            new de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorLeaderboardAssessments(
                a.id,
                count(r),
                sum(e.maxPoints)
            )
            FROM
                Result r join r.participation p join p.exercise e join e.exerciseGroup eg join eg.exam ex join r.assessor a
            WHERE
                r.completionDate is not null
                and ex.id = :#{#examId}
            GROUP BY a.id
            """)
    List<TutorLeaderboardAssessments> findTutorLeaderboardAssessmentByExamId(@Param("examId") long examId);

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date.
     * It saves the updated result in the database again.
     *
     * @param resultId the id of the result that should be submitted
     * @return the ResponseEntity with result as body
     */
    default Result submitManualAssessment(long resultId) {
        Result result = findWithEagerSubmissionAndFeedbackAndAssessorById(resultId).orElseThrow(() -> new EntityNotFoundException("Result", resultId));
        result.setCompletionDate(ZonedDateTime.now());
        save(result);
        return result;
    }

    /**
     * submit the result means it is saved with a calculated score, result string and a completion date.
     * @param result the result which should be set to submitted
     * @param exercise the exercises to which the result belongs, which is needed to get points and to determine if the result is rated or not
     * @return the saved result
     */
    default Result submitResult(Result result, Exercise exercise) {
        double maxPoints = exercise.getMaxPoints();
        double bonusPoints = Optional.ofNullable(exercise.getBonusPoints()).orElse(0.0);

        // Exam results and manual results of programming exercises are always to rated
        if (exercise.isExamExercise() || exercise instanceof ProgrammingExercise) {
            result.setRated(true);
        }
        else {
            result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        }

        result.setCompletionDate(ZonedDateTime.now());
        // Take bonus points into account to achieve a result score > 100%
        double calculatedPoints = calculateTotalPoints(result.getFeedbacks());
        double totalPoints = constrainToRange(calculatedPoints, maxPoints + bonusPoints);
        // Set score and resultString according to maxPoints, to establish results with score > 100%
        result.setScore(totalPoints, maxPoints);
        result.setResultString(totalPoints, maxPoints);

        // Workaround to prevent the assessor turning into a proxy object after saving
        var assessor = result.getAssessor();
        result = save(result);
        result.setAssessor(assessor);
        return result;
    }

    /**
     * make sure the points are between 0 and maxPoints
     * @param calculatedPoints the points which have been calculated
     * @param maxPoints the upper bound (potentially including bonus points)
     * @return a value between [0, maxPoints]
     */
    default double constrainToRange(double calculatedPoints, double maxPoints) {
        double totalPoints = Math.max(0, calculatedPoints);
        return Math.min(totalPoints, maxPoints);
    }

    /**
     * Helper function to calculate the total points of a feedback list. It loops through all assessed model elements and sums the credits up.
     * The points of an assessment model is not summed up only in the case the usageCount limit is exceeded
     * meaning the structured grading instruction was applied on the assessment model more often than allowed
     *
     * @param assessments the list of feedback items that are used to calculate the points
     * @return the total points
     */
    default double calculateTotalPoints(List<Feedback> assessments) {
        double totalPoints = 0.0;
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : assessments) {
            if (feedback.getGradingInstruction() != null) {
                totalPoints = feedback.computeTotalScore(totalPoints, gradingInstructions);
            }
            else {
                // in case no structured grading instruction was applied on the assessment model we just sum the feedback credit
                // TODO: what happens if getCredits is null?
                totalPoints += feedback.getCredits();
            }
        }
        return totalPoints;
    }

}
