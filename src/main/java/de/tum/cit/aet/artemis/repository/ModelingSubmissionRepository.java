package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.modeling.ModelingSubmission;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ModelingSubmissionRepository extends ArtemisJpaRepository<ModelingSubmission, Long> {

    @Query("""
            SELECT DISTINCT submission
            FROM ModelingSubmission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<ModelingSubmission> findByIdWithEagerResult(@Param("submissionId") Long submissionId);

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
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor", "results.assessmentNote", "participation", "participation.results" })
    Optional<ModelingSubmission> findWithResultsFeedbacksAssessorAssessmentNoteAndParticipationResultsById(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    Optional<ModelingSubmission> findWithEagerResultById(Long submissionId);

    @Query("""
            SELECT DISTINCT submission
            FROM ModelingSubmission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE submission.participation.exercise.id = :exerciseId
                AND submission.submitted = TRUE
            """)
    List<ModelingSubmission> findSubmittedByExerciseIdWithEagerResultsAndFeedback(@Param("exerciseId") Long exerciseId);

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
