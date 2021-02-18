package de.tum.in.www1.artemis.repository;

import static java.util.Arrays.asList;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;

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
    Optional<Result> findByIdWithEagerAssessor(Long resultId);

    List<Result> findByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findAllByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = "submission")
    List<Result> findByParticipationExerciseIdOrderByCompletionDateAsc(Long exerciseId);

    // TODO: cleanup unused queries

    @Query("select distinct r from Result r left join fetch r.feedbacks where r.completionDate = (select max(rr.completionDate) from Result rr where rr.assessmentType = 'AUTOMATIC' and rr.participation.exercise.id = :exerciseId and rr.participation.student.id = r.participation.student.id) and r.participation.exercise.id = :exerciseId and r.participation.student.id IS NOT NULL order by r.completionDate asc")
    List<Result> findLatestAutomaticResultsWithEagerFeedbacksForExercise(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "feedbacks")
    Optional<Result> findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(Long participationId);

    @Query("select r from Result r where r.completionDate = (select min(rr.completionDate) from Result rr where rr.participation.exercise.id = r.participation.exercise.id and rr.participation.student.id = r.participation.student.id and rr.successful = true) and r.participation.exercise.course.id = :courseId and r.successful = true order by r.completionDate asc")
    List<Result> findEarliestSuccessfulResultsForCourse(@Param("courseId") Long courseId);

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

    Long countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(long courseId, boolean rated);

    List<Result> findAllByParticipation_Exercise_CourseId(Long courseId);

    /**
     * Load a result from the database by its id together with the associated submission and the list of feedback items.
     *
     * @param resultId the id of the result to load from the database
     * @return an optional containing the result with submission and feedback list, or an empty optional if no result could be found for the given id
     */
    @EntityGraph(type = LOAD, attributePaths = { "submission", "feedbacks" })
    Optional<Result> findWithEagerSubmissionAndFeedbackById(long resultId);

    @Query("SELECT COUNT(DISTINCT p) FROM StudentParticipation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.rated = TRUE AND r.completionDate IS NOT NULL AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)")
    long countNumberOfFinishedAssessmentsForExercise(@Param("exerciseId") Long exerciseId);

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
     * @param correctionRound correction round to find completed assessments by
     * @return the number of completed assessments for the specified correction round of an exam exercise
     */
    // TODO: this query seems to be very slow on production, we should try to optimize it
    @Query("""
            SELECT COUNT(DISTINCT p)
            FROM StudentParticipation p WHERE p.exercise.id = :exerciseId
            AND p.testRun = FALSE
            AND (SELECT COUNT(r)
                FROM Result r
                WHERE r.assessor IS NOT NULL
                AND r.rated = TRUE
                AND r.submission = (select max(id) from p.submissions)
                AND r.submission.submitted = TRUE
                AND r.completionDate IS NOT NULL
                AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)
            ) >= (:correctionRound + 1L)
            """)
    long countNumberOfFinishedAssessmentsByCorrectionRoundsAndExerciseIdIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("correctionRound") long correctionRound);

    @EntityGraph(type = LOAD, attributePaths = { "feedbacks" })
    List<Result> findAllWithEagerFeedbackByAssessorIsNotNullAndParticipation_ExerciseIdAndCompletionDateIsNotNull(Long exerciseId);

    @Query("SELECT COUNT(DISTINCT p) FROM Participation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.assessmentType IN :types AND r.rated = TRUE AND r.completionDate IS NOT NULL AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)")
    long countNumberOfAssessmentsByTypeForExerciseBeforeDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    @Query("SELECT COUNT(DISTINCT p) FROM Participation p left join p.results r WHERE p.exercise.id = :exerciseId AND r.assessor IS NOT NULL AND r.assessmentType IN :types AND r.rated = FALSE AND r.completionDate IS NOT NULL AND p.exercise.dueDate IS NOT NULL AND r.submission.submissionDate > p.exercise.dueDate")
    long countNumberOfAssessmentsByTypeForExerciseAfterDueDate(@Param("exerciseId") Long exerciseId, @Param("types") List<AssessmentType> types);

    long countByAssessor_IdAndParticipation_ExerciseIdAndRatedAndCompletionDateIsNotNull(Long tutorId, Long exerciseId, boolean rated);

    /**
     * Checks if a result for the given participation exists.
     *
     * @param participationId the id of the participation to check.
     * @return true if a result for the given participation exists, false otherwise.
     */
    boolean existsByParticipationId(long participationId);

    /**
     * Returns true if there is at least one result for the given exercise.
     * @param exerciseId id of an Exercise.
     * @return true if there is a result, false if not.
     */
    boolean existsByParticipation_ExerciseId(long exerciseId);

    /**
     * Given an exerciseId and a correctionRound, return the number of assessments for that exerciseId and correctionRound that have been finished
     *
     * @param exercise  - the exercise we are interested in
     * @param correctionRounds - the correction round we want finished assessments for
     * @return an array of the number of assessments for the exercise for a given correction round
     */
    default DueDateStat[] countNumberOfFinishedAssessmentsForExerciseForCorrectionRound(Exercise exercise, int correctionRounds) {
        DueDateStat[] correctionRoundsDataStats = new DueDateStat[correctionRounds];

        for (int i = 0; i < correctionRounds; i++) {
            correctionRoundsDataStats[i] = new DueDateStat(this.countNumberOfFinishedAssessmentsByCorrectionRoundsAndExerciseIdIgnoreTestRuns(exercise.getId(), i), 0L);
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
     * Calculates the number of assessments done for each correction round.
     *
     * @param exercise the exercise for which we want to calculate the # of assessments for each correction round
     * @param examMode states whether or not the the function is called in the exam mode
     * @param totalNumberOfAssessments so total number of assessments sum up over all correction rounds
     * @return the number of assessments for each correction rounds
     */
    default DueDateStat[] countNrOfAssessmentsOfCorrectionRoundsForDashboard(Exercise exercise, boolean examMode, DueDateStat totalNumberOfAssessments) {
        if (examMode) {
            // set number of corrections specific to each correction round
            int numberOfCorrectionRounds = exercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
            return countNumberOfFinishedAssessmentsForExerciseForCorrectionRound(exercise, numberOfCorrectionRounds);
        }
        else {
            // no examMode here, so correction rounds defaults to 1 and is the same as totalNumberOfAssessments
            return new DueDateStat[] { totalNumberOfAssessments };
        }
    }

    /**
     * Given a courseId, return the number of assessments for that course that have been completed (e.g. no draft!)
     *
     * @param courseId - the course we are interested in
     * @return a number of assessments for the course
     */
    default DueDateStat countNumberOfAssessments(Long courseId) {
        return new DueDateStat(countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, true),
                countByAssessorIsNotNullAndParticipation_Exercise_CourseIdAndRatedAndCompletionDateIsNotNull(courseId, false));
    }

}
