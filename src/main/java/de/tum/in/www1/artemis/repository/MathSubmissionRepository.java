package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.MathSubmission;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the MathSubmission entity.
 */
@Repository
public interface MathSubmissionRepository extends JpaRepository<MathSubmission, Long> {

    @Query("select distinct submission from MathSubmission submission left join fetch submission.participation participation left join fetch participation.exercise left join fetch submission.results result left join fetch result.assessor left join fetch result.feedbacks where submission.id = :#{#submissionId}")
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
    @Query("select distinct s from MathSubmission s left join fetch s.results r left join fetch r.feedbacks left join fetch r.assessor where s.id = :#{#submissionId}")
    Optional<MathSubmission> findWithEagerResultsAndFeedbackById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor", "results.feedbacks" })
    Optional<MathSubmission> findWithEagerResultAndFeedbackByResults_Id(long resultId);

    /**
     * Gets all TextSubmissions which are submitted
     *
     * @param exerciseId the ID of the exercise
     * @return Set of Text Submissions
     */
    @EntityGraph(type = LOAD)
    Set<MathSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    /**
     * Gets all MathSubmissions which are submitted
     *
     * @param exerciseId the ID of the exercise
     * @param pageable   the pagination information for the query
     * @return Set of Math Submissions
     */
    @EntityGraph(type = LOAD)
    Page<MathSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId, Pageable pageable);

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
