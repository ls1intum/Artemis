package de.tum.cit.aet.artemis.modeling.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@Repository
public interface ModelingSubmissionRepository extends ArtemisJpaRepository<ModelingSubmission, Long> {

    /**
     * Writes the client-editable fields of an existing modeling submission by id.
     * <p>
     * Prefer this over {@code save} on the autosave path: the submission is detached there (no transaction spans the load
     * and the save), so Spring Data routes it through {@code merge}, which reads the row back - along with its
     * participation, exercise, exercise group, exam and course through eager associations - before writing it.
     *
     * @param submissionId    the id of the submission to update
     * @param model           the submitted model
     * @param explanationText the submitted explanation
     * @param submitted       whether the submission counts as submitted
     * @param submissionDate  when the submission was saved
     * @param type            how the submission was created
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE ModelingSubmission submission
            SET submission.model = :model,
                submission.explanationText = :explanationText,
                submission.submitted = :submitted,
                submission.submissionDate = :submissionDate,
                submission.type = :type
            WHERE submission.id = :submissionId
            """)
    void updateExistingSubmission(@Param("submissionId") long submissionId, @Param("model") String model, @Param("explanationText") String explanationText,
            @Param("submitted") boolean submitted, @Param("submissionDate") ZonedDateTime submissionDate, @Param("type") SubmissionType type);

    @Query("""
            SELECT DISTINCT submission
            FROM ModelingSubmission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<ModelingSubmission> findByIdWithEagerResultAndAssessorAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * Load the modeling submission with the given id together with its result, the feedback list of the result, the assessor of the result, the assessment note of the result,
     * its participation and all results of the participation.
     *
     * @param submissionId the id of the modeling submission that should be loaded from the database
     * @return the modeling submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(type = LOAD, attributePaths = { "results.feedbacks", "results.assessor", "results.assessmentNote", "participation.submissions.results" })
    Optional<ModelingSubmission> findWithResultsFeedbacksAssessorAssessmentNoteAndParticipationResultsById(Long submissionId);

    /**
     * Get the modeling submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the modeling submission with the given id
     */
    default ModelingSubmission findByIdWithEagerResultAndFeedbackElseThrow(Long submissionId) {
        return getValueElseThrow(findByIdWithEagerResultAndAssessorAndFeedback(submissionId), submissionId);
    }

    /**
     * Get the modeling submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the result,
     * the assessment note of the result, its participation and all results of the participation. Throws an EntityNotFoundException if no submission could be found for the given
     * id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the modeling submission with the given id
     */
    default ModelingSubmission findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(Long submissionId) {
        return getValueElseThrow(findWithResultsFeedbacksAssessorAssessmentNoteAndParticipationResultsById(submissionId), submissionId);
    }
}
