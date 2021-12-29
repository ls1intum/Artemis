package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    @Query("select distinct submission from TextSubmission submission left join fetch submission.participation participation left join fetch participation.exercise left join fetch submission.results result left join fetch result.assessor left join fetch result.feedbacks where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerParticipationExerciseResultAssessor(@Param("submissionId") long submissionId);

    /**
     * Load text submission with eager Results
     * @param submissionId the submissionId
     * @return optional text submission
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<TextSubmission> findWithEagerResultsById(long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct s from TextSubmission s left join fetch s.results r left join fetch r.feedbacks left join fetch r.assessor left join fetch s.blocks where s.id = :#{#submissionId}")
    Optional<TextSubmission> findWithEagerResultsAndFeedbackAndTextBlocksById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor", "blocks", "results.feedbacks" })
    Optional<TextSubmission> findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(long resultId);

    /**
     * Gets all open (without a result) TextSubmissions which are submitted and loads all blocks, results, and participation
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.cluster", "results", "participation", "participation.submissions" })
    List<TextSubmission> findByParticipation_ExerciseIdAndResultsIsNullAndSubmittedIsTrue(long exerciseId);

    @Query("select distinct s from TextSubmission s left join fetch s.results r left join fetch r.assessor left join fetch s.blocks where r.id = :#{#resultId}")
    Optional<TextSubmission> findByResultIdWithAssessorAndBlocks(@Param("resultId") long resultId);

    /**
     * Gets all TextSubmissions which are submitted and loads all blocks
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks" })
    List<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    /**
     * Gets all TextSubmissions which are submitted, with matching and loads all blocks
     * @param exerciseId the Id of the exercise
     * @param language language of the exercise
     * @return List of Text Submissions
     */
    List<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrueAndLanguage(long exerciseId, Language language);

    default List<TextSubmission> getTextSubmissionsWithTextBlocksByExerciseId(long exerciseId) {
        return findByParticipation_ExerciseIdAndSubmittedIsTrue(exerciseId);
    }

    @NotNull
    default TextSubmission getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(long resultId) {
        return findWithEagerResultAndTextBlocksAndFeedbackByResults_Id(resultId) // TODO should be EntityNotFoundException
                .orElseThrow(() -> new BadRequestAlertException("No text submission found for the given result.", "textSubmission", "textSubmissionNotFound"));
    }

    @NotNull
    default TextSubmission findByIdWithEagerParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return findByIdWithEagerParticipationExerciseResultAssessor(submissionId) // TODO should be EntityNotFoundException
                .orElseThrow(() -> new BadRequestAlertException("No text submission found for the given submission.", "textSubmission", "textSubmissionNotFound"));
    }

    default List<TextSubmission> getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(long exerciseId, Language language) {
        return findByParticipation_ExerciseIdAndSubmittedIsTrueAndLanguage(exerciseId, language);
    }

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return findByIdWithEagerParticipationExerciseResultAssessor(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));
    }

    default TextSubmission findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(long submissionId) {
        return findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));
    }
}
