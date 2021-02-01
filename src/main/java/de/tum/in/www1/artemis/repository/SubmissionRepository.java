package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data repository for the Submission entity.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Load submission with eager Results
     * @param submissionId the submissionId
     * @return optional submission
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<Submission> findWithEagerResultsAndAssessorById(Long submissionId);

    @Query("select distinct submission from Submission submission left join fetch submission.results r left join fetch r.feedbacks where submission.exampleSubmission = true and submission.id = :#{#submissionId}")
    Optional<Submission> findExampleSubmissionByIdWithEagerResult(@Param("submissionId") long submissionId);

    /**
     * Get all submissions of a participation
     * @param participationId the id of the participation
     * @return a list of the participation's submissions
     */
    List<Submission> findAllByParticipationId(long participationId);

    /**
     * Get all submissions of a participation and eagerly load results
     * @param participationId the id of the participation
     * @return a list of the participation's submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    List<Submission> findAllWithResultsAndAssessorByParticipationId(Long participationId);

    /**
     * Get the number of currently locked submissions for a specific user in the given course. These are all submissions for which the user started, but has not yet finished the
     * assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE EXISTS (select r1.assessor.id from submission.results r1 where r1.assessor.id = :#{#userId}) AND NOT EXISTS (select r2.completionDate from submission.results r2 where r2.completionDate is not null and r2.assessor is not null) AND submission.participation.exercise.course.id = :#{#courseId}")
    long countLockedSubmissionsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Get currently locked submissions for a specific user in the given course.
     * These are all submissions for which the user started, but has not yet finished the assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return currently locked submissions for a specific user in the given course
     */
    @Query("SELECT DISTINCT submission FROM Submission submission LEFT JOIN FETCH submission.results WHERE EXISTS (select r1.assessor.id from submission.results r1 where r1.assessor.id = :#{#userId}) AND NOT EXISTS (select r2.completionDate from submission.results r2 where r2.completionDate is not null AND r2.assessor is not null) AND submission.participation.exercise.course.id = :#{#courseId}")
    List<Submission> getLockedSubmissionsAndResultsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Checks if a submission for the given participation exists.
     *
     * @param participationId the id of the participation to check
     * @return true if a submission for the given participation exists, false otherwise
     */
    boolean existsByParticipationId(long participationId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission) AND submission.participation.exercise.course.id = :#{#courseId} AND submission.submitted = TRUE AND (submission.submissionDate <= submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") long courseId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission) AND submission.participation.exercise.course.id = :#{#courseId} AND submission.submitted = TRUE AND submission.participation.exercise.dueDate IS NOT NULL AND submission.submissionDate > submission.participation.exercise.dueDate")
    long countByCourseIdSubmittedAfterDueDate(@Param("courseId") long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("SELECT COUNT (DISTINCT p) FROM StudentParticipation p WHERE p.exercise.id = :#{#exerciseId} AND EXISTS (SELECT s FROM Submission s WHERE s.participation.id = p.id AND s.submitted = TRUE AND (p.exercise.dueDate IS NULL OR s.submissionDate <= p.exercise.dueDate))")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") long exerciseId);

    /**
     * Should be used for exam dashboard to ignore test run submissions
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p
            WHERE p.exercise.id = :#{#exerciseId}
            AND (p.testRun = FALSE OR p.testRun IS NULL)
            AND EXISTS (SELECT s
                FROM Submission s
                WHERE s.participation.id = p.id
                AND s.submitted = TRUE
                AND (p.exercise.dueDate IS NULL
                    OR s.submissionDate <= p.exercise.dueDate))
            """)
    long countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    /**
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("SELECT COUNT (DISTINCT p) FROM StudentParticipation p WHERE p.exercise.id = :#{#exerciseId} AND EXISTS (SELECT s FROM Submission s WHERE s.participation.id = p.id AND s.submitted = TRUE AND (p.exercise.dueDate IS NOT NULL AND s.submissionDate > p.exercise.dueDate))")
    long countByExerciseIdSubmittedAfterDueDate(@Param("exerciseId") long exerciseId);

    /**
     * Returns submissions for a exercise. Returns only a submission that has a result with a matching assessor. Since the results list may also contain
     * automatic results but those results do not have an assessor, hibernate simply sets null values for them. Make sure to use a different query if you need
     * your submission to have all its results set.
     *
     * @param exerciseId the exercise id we are interested in
     * @param assessor the assessor we are interested in
     * @param <T> the type of the submission
     * @return the submissions belonging to the exercise id, which have been assessed by the given assessor
     */
    @Query("SELECT DISTINCT submission FROM Submission submission left join fetch submission.results r left join fetch r.assessor a WHERE submission.participation.exercise.id = :#{#exerciseId} AND :#{#assessor} = a")
    <T extends Submission> List<T> findAllByParticipationExerciseIdAndResultAssessor(@Param("exerciseId") Long exerciseId, @Param("assessor") User assessor);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct submission from Submission submission left join fetch submission.results r left join fetch r.feedbacks left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<Submission> findWithEagerResultAndFeedbackById(long submissionId);
}
