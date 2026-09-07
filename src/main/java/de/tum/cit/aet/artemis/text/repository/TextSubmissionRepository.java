package de.tum.cit.aet.artemis.text.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@Conditional(TextEnabled.class)
@Lazy
@Repository
public interface TextSubmissionRepository extends ArtemisJpaRepository<TextSubmission, Long> {

    /**
     * Writes the client-editable fields of an existing text submission by id.
     * <p>
     * Prefer this over {@code save} on the autosave path: the submission is detached there (no transaction spans the load
     * and the save), so Spring Data routes it through {@code merge}, which reads the row back - along with its
     * participation, exercise, exercise group, exam and course through eager associations - before writing it.
     *
     * @param submissionId   the id of the submission to update
     * @param text           the submitted text
     * @param language       the language of the submitted text
     * @param submitted      whether the submission counts as submitted
     * @param submissionDate when the submission was saved
     * @param type           how the submission was created
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE TextSubmission submission
            SET submission.text = :text,
                submission.language = :language,
                submission.submitted = :submitted,
                submission.submissionDate = :submissionDate,
                submission.type = :type
            WHERE submission.id = :submissionId
            """)
    void updateExistingSubmission(@Param("submissionId") long submissionId, @Param("text") String text, @Param("language") Language language, @Param("submitted") boolean submitted,
            @Param("submissionDate") ZonedDateTime submissionDate, @Param("type") SubmissionType type);

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
