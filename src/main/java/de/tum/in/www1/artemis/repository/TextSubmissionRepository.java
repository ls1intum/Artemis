package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks", "results.assessor", "participation.exercise" })
    Optional<TextSubmission> findWithEagerParticipationExerciseResultAssessorById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks", "results.assessor", "results.assessmentNote", "participation.exercise" })
    Optional<TextSubmission> findWithEagerParticipationExerciseResultAssessorAssessmentNoteById(long submissionId);

    /**
     * Load text submission only
     *
     * @param submissionId the submissionId
     * @return optional text submission
     */
    @NotNull
    default TextSubmission findByIdElseThrow(long submissionId) {
        return findById(submissionId).orElseThrow(() -> new EntityNotFoundException("Text Submission", submissionId));
    }

    /**
     * Load text submission with eager Results
     *
     * @param submissionId the submissionId
     * @return optional text submission
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<TextSubmission> findWithEagerResultsById(long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @EntityGraph(type = LOAD, attributePaths = { "results.assessor", "results.feedbacks", "blocks" })
    Optional<TextSubmission> findWithEagerResultsAndFeedbackAndTextBlocksById(long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor and assessment note
     */
    @Query("select distinct s from TextSubmission s left join fetch s.results r left join fetch r.feedbacks left join fetch r.assessor left join fetch s.blocks left join fetch r.assessmentNote where s.id = :#{#submissionId}")
    Optional<TextSubmission> findWithEagerResultsAndFeedbackAndTextBlocksAndAssessmentNoteById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor", "blocks", "results.feedbacks" })
    Optional<TextSubmission> findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(long resultId);

    /**
     * Gets all TextSubmissions which are submitted and loads all blocks
     *
     * @param exerciseId the ID of the exercise
     * @return Set of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks" })
    Set<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    @NotNull
    default TextSubmission getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(long resultId) {
        return findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(resultId) // TODO should be EntityNotFoundException
                .orElseThrow(() -> new BadRequestAlertException("No text submission found for the given result.", "textSubmission", "textSubmissionNotFound"));
    }

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return findWithEagerParticipationExerciseResultAssessorById(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));
    }

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorAssessmentNoteElseThrow(long submissionId) {
        return findWithEagerParticipationExerciseResultAssessorAssessmentNoteById(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));
    }

    default TextSubmission findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(long submissionId) {
        return findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));
    }
}
