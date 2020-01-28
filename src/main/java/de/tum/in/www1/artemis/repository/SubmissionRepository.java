package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Spring Data repository for the Submission entity.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("select distinct submission from Submission submission left join fetch submission.result r left join fetch r.feedbacks where submission.exampleSubmission = true and submission.id = :#{#submissionId}")
    Optional<Submission> findSubmissionWithExampleSubmissionByIdWithEagerResult(long submissionId);

    /* Get all submissions from a participation_id and load result at the same time */
    @EntityGraph(attributePaths = { "result" })
    List<Submission> findAllByParticipationId(Long participationId);

    /**
     * Get the number of currently locked submissions for a specific user in the given course. These are all submissions for which the user started, but has not yet finished the
     * assessment.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return the number of currently locked submissions for a specific user in the given course
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE submission.result.assessor.id = :#{#userId} AND submission.result.completionDate is null AND submission.participation.exercise.course.id = :#{#courseId}")
    long countLockedSubmissionsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Checks if a submission for the given participation exists.
     *
     * @param participationId the id of the participation to check
     * @return true if a submission for the given participation exists, false otherwise
     */
    boolean existsByParticipationId(long participationId);

    List<Submission> findByParticipationId(long participationId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE submission.participation.exercise.course.id = :#{#courseId} AND submission.submitted = TRUE AND (submission.submissionDate < submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") Long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    @Query("SELECT COUNT (DISTINCT submission) FROM Submission submission WHERE submission.participation.exercise.id = :#{#exerciseId} AND submission.submitted = TRUE AND (submission.submissionDate < submission.participation.exercise.dueDate OR submission.participation.exercise.dueDate IS NULL)")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") Long exerciseId);
}
