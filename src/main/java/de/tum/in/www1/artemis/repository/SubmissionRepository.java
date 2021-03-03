package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;

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
    @Query("""
            SELECT COUNT (DISTINCT s) FROM Submission s left join s.results r
                WHERE r.assessor.id = :userId
                AND r.completionDate IS NULL
                AND  s.participation.exercise.course.id = :courseId
            """)
    long countLockedSubmissionsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Get the number of currently locked submissions for a specific user in the given exam. These are all submissions for which the user started, but has not yet finished the
     * assessment.
     *
     * @param userId   the id of the user
     * @param examId the id of the exam
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT COUNT (DISTINCT s) FROM Submission s left join s.results r
                WHERE r.assessor.id = :userId
                AND r.completionDate IS NULL
                AND s.participation.exercise.exerciseGroup.exam.id= :examId
            """)
    long countLockedSubmissionsByUserIdAndExamId(@Param("userId") Long userId, @Param("examId") Long examId);

    /**
     * Get currently locked submissions for a specific user in the given course.
     * These are all submissions for which the user started, but has not yet finished the assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT DISTINCT submission FROM Submission submission LEFT JOIN FETCH submission.results
                WHERE EXISTS (select r1.assessor.id from submission.results r1
                    where r1.assessor.id = :#{#userId})
                AND NOT EXISTS (select r2.completionDate from submission.results r2
                    where r2.completionDate is not null
                    AND r2.assessor is not null)
                AND submission.participation.exercise.course.id = :#{#courseId}
                """)
    List<Submission> getLockedSubmissionsAndResultsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Get all currently locked submissions for all users in the given exam.
     * These are all submissions for which users started, but did not yet finish the assessment.
     *
     * @param examId the id of the course
     * @return currently locked submissions for the given exam
     */
    @Query("""
            SELECT DISTINCT s FROM Submission s LEFT JOIN FETCH s.results r
                WHERE r.assessor.id IS NOT NULL
                AND r.assessmentType <> 'AUTOMATIC'
                AND r.completionDate IS NULL
                AND s.participation.exercise.exerciseGroup.exam.id = :examId
            """)
    List<Submission> getLockedSubmissionsAndResultsByExamId(@Param("examId") Long examId);

    /**
     * Checks if a submission for the given participation exists.
     *
     * @param participationId the id of the participation to check
     * @return true if a submission for the given participation exists, false otherwise
     */
    boolean existsByParticipationId(long participationId);

    /**
     * Count number of in-time submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("""
            SELECT COUNT (DISTINCT submission) FROM Submission submission
                WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND submission.participation.exercise.course.id = :#{#courseId}
                AND submission.submitted = TRUE
                AND (submission.submissionDate <= submission.participation.exercise.dueDate
                    OR submission.participation.exercise.dueDate IS NULL)
            """)
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") long courseId);

    /**
     * Count the number of in-time submissions for an exam. Only submissions for Text, Modeling and File Upload exercises are included.
     *
     * @param examId -  the exam id we are interested in
     * @return the number of submissions belonging to the exam id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("""
            SELECT COUNT (DISTINCT submission) FROM Submission submission
                WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND submission.participation.exercise.exerciseGroup.exam.id = :#{#examId}
                AND submission.submitted = TRUE
                AND submission.participation.testRun = FALSE
            """)
    long countByExamIdSubmittedSubmissionsIgnoreTestRuns(@Param("examId") long examId);

    /**
     * Count number of late submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     *
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("""
            SELECT COUNT (DISTINCT submission) FROM Submission submission
                WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND submission.participation.exercise.course.id = :#{#courseId}
                AND submission.submitted = TRUE
                AND submission.participation.exercise.dueDate IS NOT NULL
                AND submission.submissionDate > submission.participation.exercise.dueDate
            """)
    long countByCourseIdSubmittedAfterDueDate(@Param("courseId") long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p
                WHERE p.exercise.id = :#{#exerciseId}
                AND EXISTS (SELECT s FROM Submission s
                    WHERE s.participation.id = p.id
                    AND s.submitted = TRUE
                    AND (p.exercise.dueDate IS NULL
                        OR s.submissionDate <= p.exercise.dueDate))
            """)
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") long exerciseId);

    /**
     * Should be used for exam dashboard to ignore test run submissions
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p join  p.submissions s
            WHERE p.exercise.id = :#{#exerciseId}
            AND p.testRun = FALSE
            AND s.participation.id = p.id
            AND s.submitted = TRUE
            AND (p.exercise.dueDate IS NULL OR s.submissionDate <= p.exercise.dueDate)
            """)
    long countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    /**
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p
                WHERE p.exercise.id = :#{#exerciseId}
                AND EXISTS (SELECT s FROM Submission s
                    WHERE s.participation.id = p.id
                    AND s.submitted = TRUE
                    AND (p.exercise.dueDate IS NOT NULL
                    AND s.submissionDate > p.exercise.dueDate))
            """)
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
    @Query("""
            SELECT DISTINCT submission FROM Submission submission LEFT JOIN FETCH submission.results r LEFT JOIN FETCH r.assessor a
            WHERE submission.participation.exercise.id = :#{#exerciseId} AND :#{#assessor} = a
            """)
    <T extends Submission> List<T> findAllByParticipationExerciseIdAndResultAssessor(@Param("exerciseId") Long exerciseId, @Param("assessor") User assessor);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("""
            SELECT DISTINCT submission FROM Submission submission LEFT JOIN FETCH submission.results r LEFT JOIN FETCH r.feedbacks LEFT JOIN FETCH  r.assessor
            WHERE submission.id = :#{#submissionId}
            """)
    Optional<Submission> findWithEagerResultAndFeedbackById(@Param("submissionId") long submissionId);

    /**
     * Initializes a new text, modeling or file upload submission (depending on the type of the given exercise), connects it with the given participation and stores it in the
     * database.
     *
     * @param participation   the participation for which the submission should be initialized
     * @param exercise        the corresponding exercise, should be either a text, modeling or file upload exercise, otherwise it will instantly return and not do anything
     * @param submissionType  type for the submission to be initialized
     * @return a new submission for the given type connected to the given participation
     */
    default Submission initializeSubmission(Participation participation, Exercise exercise, SubmissionType submissionType) {

        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(submissionType);
        submission.setParticipation(participation);
        save(submission);
        participation.addSubmission(submission);
        return submission;
    }

    /**
     * Count number of submissions for exercise.
     * @param exerciseId the exercise id we are interested in
     * @param examMode should be set to ignore the test run submissions
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true, separated into before and after the due date
     */
    default DueDateStat countSubmissionsForExercise(long exerciseId, boolean examMode) {
        if (examMode) {
            return new DueDateStat(countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exerciseId), 0L);
        }
        return new DueDateStat(countByExerciseIdSubmittedBeforeDueDate(exerciseId), countByExerciseIdSubmittedAfterDueDate(exerciseId));
    }
}
