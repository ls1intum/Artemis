package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Spring Data repository for the Submission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SubmissionRepository extends ArtemisJpaRepository<Submission, Long> {

    /**
     * Load submission with eager Results
     *
     * @param submissionId the submissionId
     * @return optional submission
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<Submission> findWithEagerResultsAndAssessorById(long submissionId);

    @Query("""
            SELECT DISTINCT submission
            FROM Submission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE submission.exampleSubmission = TRUE
                AND submission.id = :submissionId
            """)
    Optional<Submission> findExampleSubmissionByIdWithEagerResult(@Param("submissionId") long submissionId);

    /**
     * Get all submissions of a participation
     *
     * @param participationId the id of the participation
     * @return a list of the participation's submissions
     */
    List<Submission> findAllByParticipationId(long participationId);

    Optional<Submission> findByParticipationIdOrderBySubmissionDateDesc(long participationId);

    List<Submission> findByParticipation_Exercise_ExerciseGroup_Exam_Id(long examId);

    /**
     * Get all submissions of a participation and eagerly load results
     *
     * @param participationId the id of the participation
     * @return a list of the participation's submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    List<Submission> findAllWithResultsAndAssessorByParticipationId(Long participationId);

    /**
     * Get all submissions with their results by the submission ids
     *
     * @param submissionIds the ids of the submissions which should be retrieved
     * @return a list of submissions with their results eagerly loaded
     */
    @Query("""
            SELECT DISTINCT s
            FROM Submission s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
            WHERE s.id IN :submissionIds
            """)
    List<Submission> findBySubmissionIdsWithEagerResults(@Param("submissionIds") List<Long> submissionIds);

    /**
     * Get the number of currently locked submissions for a specific user in the given course. These are all submissions for which the user started, but has not yet finished the
     * assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                LEFT JOIN s.results r
            WHERE r.assessor.id = :userId
                AND r.completionDate IS NULL
                AND s.participation.exercise.course.id = :courseId
            """)
    long countLockedSubmissionsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Get the number of currently locked submissions for the given course.
     *
     * @param courseId the id of the course
     * @return the number of currently locked submissions for the given course
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                LEFT JOIN s.results r
            WHERE r.completionDate IS NULL
                AND s.participation.exercise.course.id = :courseId
            """)
    long countLockedSubmissionsByCourseId(@Param("courseId") Long courseId);

    /**
     * Get the number of currently locked submissions for a specific user in the given exam. These are all submissions for which the user started, but has not yet finished the
     * assessment.
     *
     * @param userId the id of the user
     * @param examId the id of the exam
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                LEFT JOIN s.results r
            WHERE r.assessor.id = :userId
                AND r.completionDate IS NULL
                AND s.participation.exercise.exerciseGroup.exam.id = :examId
            """)
    long countLockedSubmissionsByUserIdAndExamId(@Param("userId") Long userId, @Param("examId") Long examId);

    /**
     * Get the number of currently locked submissions for a given exam. These are all submissions for which users started, but have not yet finished the
     * assessments.
     *
     * @param examId the id of the exam
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                LEFT JOIN s.results r
            WHERE r.assessor.id IS NOT NULL
                AND r.completionDate IS NULL
                AND s.participation.exercise.exerciseGroup.exam.id = :examId
            """)
    long countLockedSubmissionsByExamId(@Param("examId") Long examId);

    /**
     * Get the number of currently locked submissions for a given exam. These are all submissions for which users started, but have not yet finished the
     * assessments.
     *
     * @param exerciseId the id of the exam
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                LEFT JOIN s.results r
            WHERE r.assessor.id IS NOT NULL
                AND r.completionDate IS NULL
                AND s.participation.exercise.id = :exerciseId
            """)
    long countLockedSubmissionsByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Get currently locked submissions for a specific user in the given course.
     * These are all submissions for which the user started, but has not yet finished the assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return currently locked submissions for a specific user in the given course
     */
    @Query("""
            SELECT DISTINCT submission
            FROM Submission submission
                LEFT JOIN FETCH submission.results r
            WHERE r.assessor.id = :userId
                AND r.completionDate IS NULL
                AND submission.participation.exercise.course.id = :courseId
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
            SELECT DISTINCT s
            FROM Submission s
                LEFT JOIN FETCH s.results r
            WHERE r.assessor.id IS NOT NULL
                AND r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
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
     * @param exerciseIds the exercise ids of the course we are interested in
     * @return the number of submissions belonging to the exercise ids, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise
     *         due date at all
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                JOIN s.participation p
                JOIN p.exercise e
            WHERE TYPE(s) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND e.id IN :exerciseIds
                AND s.submitted = TRUE
                AND (s.submissionDate <= e.dueDate OR e.dueDate IS NULL)
            """)
    long countAllByExerciseIdsSubmittedBeforeDueDate(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Count the number of in-time submissions for an exam. Only submissions for Text, Modeling and File Upload exercises are included.
     *
     * @param examId - the exam id we are interested in
     * @return the number of submissions belonging to the exam id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("""
            SELECT COUNT(DISTINCT submission)
            FROM Submission submission
            WHERE TYPE(submission) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND submission.participation.exercise.exerciseGroup.exam.id = :examId
                AND submission.submitted = TRUE
                AND submission.participation.testRun = FALSE
            """)
    long countByExamIdSubmittedSubmissionsIgnoreTestRuns(@Param("examId") long examId);

    /**
     * Count number of late submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     *
     * @param exerciseIds the ids of the exercises belonging to the course we are interested in
     * @return the number of submissions belonging to the course, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM Submission s
                JOIN s.participation p
                JOIN p.exercise e
            WHERE TYPE(s) IN (ModelingSubmission, TextSubmission, FileUploadSubmission)
                AND e.id IN :exerciseIds
                AND s.submitted = TRUE
                AND e.dueDate IS NOT NULL
                AND s.submissionDate > e.dueDate
            """)
    long countAllByExerciseIdsSubmittedAfterDueDate(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT COUNT(DISTINCT p)
            FROM StudentParticipation p
                JOIN p.exercise e
                JOIN p.submissions s
            WHERE e.id = :exerciseId
                AND s.submitted = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND (e.dueDate IS NULL OR s.submissionDate <= e.dueDate)
            """)
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") long exerciseId);

    /**
     * Should be used for exam dashboard to ignore test run submissions
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT COUNT(DISTINCT p)
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN p.exercise e
            WHERE e.id = :exerciseId
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND (e.dueDate IS NULL OR s.submissionDate <= e.dueDate)
            """)
    long countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    /**
     * Should be used for exam dashboard to ignore test run submissions
     *
     * @param exerciseIds the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                p.exercise.id,
                COUNT(DISTINCT p)
            )
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN p.exercise e
            WHERE e.id IN :exerciseIds
                AND p.testRun = FALSE
                AND s.submitted = TRUE
                AND (e.dueDate IS NULL OR s.submissionDate <= e.dueDate)
            GROUP BY p.exercise.id
            """)
    List<ExerciseMapEntryDTO> countByExerciseIdsSubmittedBeforeDueDateIgnoreTestRuns(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Calculate the number of submitted submissions for the given exercise. This query uses the participations to make sure that each student is only counted once
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true
     */
    @Query("""
            SELECT COUNT(DISTINCT p)
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND s.submitted = TRUE
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);

    /**
     * Calculate the number of submissions for the given exercise by the given student.
     *
     * @param exerciseId   the exercise id we are interested in
     * @param studentLogin the login of the student we are interested in
     * @return the number of submissions belonging to the exercise and student id
     */
    @Query("""
            SELECT COUNT(DISTINCT s)
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.student.login = :studentLogin
            """)
    int countByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("studentLogin") String studentLogin);

    /**
     * @param exerciseIds the exercise ids we are interested in
     * @return the numbers of submissions belonging to each exercise id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                e.id,
                COUNT(DISTINCT p)
            )
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN p.exercise e
            WHERE e.id IN :exerciseIds
                AND s.submitted = TRUE
                AND s.submissionDate > e.dueDate
            GROUP BY e.id
            """)
    List<ExerciseMapEntryDTO> countByExerciseIdsSubmittedAfterDueDate(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Returns submissions for an exercise. Returns only a submission that has a result with a matching assessor. Since the results list may also contain
     * automatic results but those results do not have an assessor, hibernate simply sets null values for them. Make sure to use a different query if you need
     * your submission to have all its results set.
     *
     * @param exerciseId the exercise id we are interested in
     * @param assessor   the assessor we are interested in
     * @param <T>        the type of the submission
     * @return the submissions belonging to the exercise id, which have been assessed by the given assessor
     */
    @Query("""
            SELECT DISTINCT submission
            FROM Submission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.assessor a
            WHERE submission.participation.exercise.id = :exerciseId
                AND :assessor = a
                AND submission.participation.testRun = FALSE
            """)
    <T extends Submission> List<T> findAllByParticipationExerciseIdAndResultAssessorIgnoreTestRuns(@Param("exerciseId") Long exerciseId, @Param("assessor") User assessor);

    @Query("""
            SELECT DISTINCT submission
            FROM Submission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH r.assessmentNote
            WHERE submission.id = :submissionId
            """)
    Optional<Submission> findWithEagerResultAndFeedbackAndAssessmentNoteById(@Param("submissionId") long submissionId);

    @Query("""
            SELECT DISTINCT submission
            FROM Submission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH r.assessmentNote
                LEFT JOIN FETCH submission.participation p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE submission.id = :submissionId
            """)
    Optional<Submission> findWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudentsById(@Param("submissionId") long submissionId);

    /**
     * Initializes a new text, modeling or file upload submission (depending on the type of the given exercise), connects it with the given participation and stores it in the
     * database.
     *
     * @param participation  the participation for which the submission should be initialized
     * @param exercise       the corresponding exercise, should be either a text, modeling or file upload exercise, otherwise it will instantly return and not do anything
     * @param submissionType type for the submission to be initialized
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
        submission = save(submission);
        participation.addSubmission(submission);
        return submission;
    }

    /**
     * Count number of submissions for exercise.
     *
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true, separated into before and after the due date
     */
    default DueDateStat countSubmissionsForExercise(long exerciseId) {
        return new DueDateStat(countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exerciseId), 0L);
    }

    /**
     * Get the submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the
     * result and the assessment note of the result.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     * @throws EntityNotFoundException if no submission could be found for the given id
     */
    default Submission findOneWithEagerResultAndFeedbackAndAssessmentNote(long submissionId) {
        return getValueElseThrow(this.findWithEagerResultAndFeedbackAndAssessmentNoteById(submissionId), submissionId);
    }

    /**
     * Get the submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the result and the
     * team students of the participation.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     * @throws EntityNotFoundException if no submission could be found for the given id
     */
    default Submission findOneWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudents(long submissionId) {
        return getValueElseThrow(findWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudentsById(submissionId), submissionId);
    }

    /**
     * Get the submission with the given id from the database. The submission is loaded together with its results and the assessors.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     * @throws EntityNotFoundException if no submission could be found for the given id
     */
    default Submission findByIdWithResultsElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerResultsAndAssessorById(submissionId), submissionId);
    }

    /**
     * Gets all latest submitted Submissions, only one per participation
     *
     * @param exerciseId the ID of the exercise
     * @param pageable   the pagination information for the query
     * @return Page of Submissions
     */
    @Query("""
            SELECT s
            FROM Submission s
            WHERE s.participation.exercise.id = :exerciseId
                AND s.submitted = TRUE
                AND s.submissionDate = (
                    SELECT MAX(s2.submissionDate)
                    FROM Submission s2
                    WHERE s2.participation.id = s.participation.id
                        AND s2.submitted = TRUE
                )
            """)
    Page<Submission> findLatestSubmittedSubmissionsByExerciseId(@Param("exerciseId") long exerciseId, Pageable pageable);

    /**
     * Gets all submitted Submissions for the given exercise. Note that you usually only want the latest submissions.
     *
     * @param exerciseId the ID of the exercise
     * @return Set of Submissions
     */
    Set<Submission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    /**
     * GChecks if unassessed Quiz Submissions exist for the given exam
     *
     * @param examId the ID of the exam
     * @return boolean indicating if there are unassessed Quiz Submission
     */
    @Query("""
                SELECT COUNT(p.exercise) > 0
                FROM StudentParticipation p
                    JOIN p.submissions s
                    LEFT JOIN s.results r
                WHERE p.exercise.exerciseGroup.exam.id = :examId
                    AND p.testRun IS FALSE
                    AND TYPE(s) = QuizSubmission
                    AND s.submitted IS TRUE
                    AND r.id IS NULL
            """)
    boolean existsUnassessedQuizzesByExamId(@Param("examId") long examId);

    /**
     * Checks if unsubmitted text and modeling submissions exist for the exam with the given id
     *
     * @param examId the ID of the exam
     * @return boolean indicating if there are unsubmitted text and modelling submissions
     */
    @Query("""
            SELECT COUNT(p.exercise) > 0
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.exerciseGroup.exam.id = :examId
                AND p.testRun IS FALSE
                AND TYPE(s) IN (TextSubmission, ModelingSubmission)
                AND (s.submitted IS NULL OR s.submitted IS FALSE)
                AND s.submissionDate IS NULL
            """)
    boolean existsUnsubmittedExercisesByExamId(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(s) > 0
            FROM Submission s
                LEFT JOIN s.participation p
                LEFT JOIN p.exercise e
                LEFT JOIN p.student st
                LEFT JOIN p.team t
                LEFT JOIN t.students ts
            WHERE e.id = :exerciseId
                AND (st.id = :userId OR ts.id = :userId)
                AND s.submitted = TRUE
            """)
    boolean existsByExerciseIdAndParticipantIdAndSubmitted(@Param("exerciseId") long exerciseId, @Param("userId") long userId);
}
