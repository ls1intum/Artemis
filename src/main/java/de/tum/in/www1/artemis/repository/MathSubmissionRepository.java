package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.math.MathSubmission;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the MathSubmission entity.
 */
@Repository
public interface MathSubmissionRepository extends JpaRepository<MathSubmission, Long> {

    @Query("""
                SELECT DISTINCT submission FROM MathSubmission submission
                LEFT JOIN FETCH submission.participation participation
                LEFT JOIN FETCH participation.exercise
                LEFT JOIN FETCH submission.results result
                LEFT JOIN FETCH result.assessor
                LEFT JOIN FETCH result.feedbacks
                WHERE submission.id = :#{#submissionId}
            """)
    Optional<MathSubmission> findByIdWithEagerParticipationExerciseResultAssessor(@Param("submissionId") long submissionId);

    /**
     * Load math submission only
     *
     * @param submissionId the submissionId
     * @return optional text submission
     */
    @NotNull
    default MathSubmission findByIdElseThrow(long submissionId) {
        return findById(submissionId).orElseThrow(() -> new EntityNotFoundException("Text Submission", submissionId));
    }

    /**
     * Load math submission with eager Results
     *
     * @param submissionId the submissionId
     * @return optional text submission
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<MathSubmission> findWithEagerResultsById(long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("""
                SELECT DISTINCT s FROM MathSubmission s
                    LEFT JOIN FETCH s.results r
                    LEFT JOIN FETCH r.feedbacks
                    LEFT JOIN FETCH r.assessor
                WHERE s.id = :#{#submissionId}
            """)
    Optional<MathSubmission> findWithEagerResultsAndFeedbackById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor", "results.feedbacks" })
    Optional<MathSubmission> findWithEagerResultAndFeedbackByResults_Id(long resultId);

    @NotNull
    default MathSubmission getSubmissionWithResultAndFeedbackByResultIdElseThrow(long resultId) {
        return findWithEagerResultAndFeedbackByResults_Id(resultId) // TODO should be EntityNotFoundException
                .orElseThrow(() -> new BadRequestAlertException("No math submission found for the given result.", "mathSubmission", "mathSubmissionNotFound"));
    }

    @NotNull
    default MathSubmission findByIdWithParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return findByIdWithEagerParticipationExerciseResultAssessor(submissionId).orElseThrow(() -> new EntityNotFoundException("MathSubmission", submissionId));
    }

    default MathSubmission findByIdWithEagerResultsAndFeedbackElseThrow(long submissionId) {
        return findWithEagerResultsAndFeedbackById(submissionId).orElseThrow(() -> new EntityNotFoundException("MathSubmission", submissionId));
    }
}
