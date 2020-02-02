package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextSubmission;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    @Query("select distinct submission from TextSubmission submission left join fetch submission.participation participation left join fetch participation.exercise left join fetch submission.result result left join fetch result.assessor left join fetch submission.blocks where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerParticipationExerciseResultAssessorAndBlocks(@Param("submissionId") Long submissionId);

    @Query("select distinct submission from TextSubmission submission left join fetch submission.result r left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultAndAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct submission from TextSubmission submission left join fetch submission.result r left join fetch r.feedbacks left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT textSubmission) FROM TextSubmission textSubmission WHERE textSubmission.participation.exercise.course.id = :#{#courseId} AND textSubmission.submitted = TRUE AND (textSubmission.submissionDate < textSubmission.participation.exercise.dueDate OR textSubmission.participation.exercise.dueDate IS NULL)")
    long countByCourseIdSubmittedBeforeDueDate(@Param("courseId") Long courseId);

    /**
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date or no exercise
     *         due date at all
     */
    @Query("SELECT COUNT (DISTINCT textSubmission) FROM TextSubmission textSubmission WHERE textSubmission.participation.exercise.id = :#{#exerciseId} AND textSubmission.submitted = TRUE AND (textSubmission.submissionDate < textSubmission.participation.exercise.dueDate OR textSubmission.participation.exercise.dueDate IS NULL)")
    long countByExerciseIdSubmittedBeforeDueDate(@Param("exerciseId") Long exerciseId);

    /**
     * Gets all open (without a result) TextSubmissions which are submitted and loads all blocks, results, and participation
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(attributePaths = { "blocks", "result", "participation" })
    List<TextSubmission> findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(Long exerciseId);
}
