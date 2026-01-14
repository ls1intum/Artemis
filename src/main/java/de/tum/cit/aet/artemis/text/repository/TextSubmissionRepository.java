package de.tum.cit.aet.artemis.text.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@Conditional(TextEnabled.class)
@Lazy
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
    @EntityGraph(type = LOAD, attributePaths = { "results.assessor" })
    Optional<TextSubmission> findWithEagerResultsAssessorById(long submissionId);

    @NonNull
    default TextSubmission findWithEagerResultsAssessorByIdElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerResultsAssessorById(submissionId), submissionId);
    }

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @EntityGraph(type = LOAD, attributePaths = { "results.assessor", "results.feedbacks", "blocks" })
    Optional<TextSubmission> findWithEagerResultsAndFeedbackAndTextBlocksById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results.assessor", "blocks", "results.feedbacks" })
    Optional<TextSubmission> findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(long resultId);

    @NonNull
    default TextSubmission getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(long resultId) {
        return getValueElseThrow(findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(resultId));
    }

    @NonNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorAssessmentNoteElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerParticipationExerciseResultAssessorAssessmentNoteById(submissionId), submissionId);
    }

    default TextSubmission findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId), submissionId);
    }
}
