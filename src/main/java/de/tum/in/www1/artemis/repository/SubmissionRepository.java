package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Spring Data repository for the Submission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * @param submitted  choose which submitted state you want
     * @param exerciseId the id of the exercise you want the stats about
     * @return number of submission for the given exerciseId, with the submitted status expressed by the flag
     */
    long countBySubmittedAndParticipation_Exercise_Id(boolean submitted, Long exerciseId);

    @Query("select submission from Submission submission where type(submission) in (ModelingSubmission, TextSubmission) and submission.submitted = false and not submission.participation is null")
    List<Submission> findAllUnsubmittedModelingAndTextSubmissions();

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

    Optional<Submission> findFirstByParticipationIdOrderBySubmissionDate(Long participationId);
}
