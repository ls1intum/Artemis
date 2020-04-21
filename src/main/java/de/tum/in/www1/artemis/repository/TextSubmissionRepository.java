package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

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
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    @Query("SELECT DISTINCT submission FROM TextSubmission submission LEFT JOIN FETCH submission.participation participation LEFT JOIN FETCH participation.exercise LEFT JOIN FETCH submission.result result LEFT JOIN FETCH result.assessor LEFT JOIN FETCH result.feedbacks WHERE submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerParticipationExerciseResultAssessor(@Param("submissionId") Long submissionId);

    @Query("SELECT DISTINCT submission FROM TextSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.assessor WHERE submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultAndAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("SELECT DISTINCT submission FROM TextSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.feedbacks LEFT JOIN FETCH r.assessor WHERE submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * Gets all open (without a result) TextSubmissions which are submitted and loads all blocks, results, and participation
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.cluster", "result", "participation", "participation.submissions" })
    List<TextSubmission> findAllByParticipationExerciseIdAndResultIsNullAndSubmittedIsTrue(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.assessor", "blocks" })
    Optional<TextSubmission> findByResultId(Long resultId);
}
