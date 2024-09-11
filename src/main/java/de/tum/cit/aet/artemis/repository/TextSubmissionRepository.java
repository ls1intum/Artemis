package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TextSubmissionRepository extends ArtemisJpaRepository<TextSubmission, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks", "results.assessor", "participation.exercise" })
    Optional<TextSubmission> findWithEagerParticipationExerciseResultAssessorById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks", "results.assessor", "results.assessmentNote", "participation.exercise" })
    Optional<TextSubmission> findWithEagerParticipationExerciseResultAssessorAssessmentNoteById(long submissionId);

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
        return getValueElseThrow(findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(resultId));
    }

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerParticipationExerciseResultAssessorById(submissionId), submissionId);
    }

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorAssessmentNoteElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerParticipationExerciseResultAssessorAssessmentNoteById(submissionId), submissionId);
    }

    default TextSubmission findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId), submissionId);
    }
}
