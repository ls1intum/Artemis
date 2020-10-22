package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.domain.enumeration.Language;
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

    @Query("select distinct submission from TextSubmission submission left join fetch submission.participation participation left join fetch participation.exercise left join fetch submission.result result left join fetch result.assessor left join fetch result.feedbacks where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerParticipationExerciseResultAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct s from TextSubmission s left join fetch s.result r left join fetch r.feedbacks left join fetch r.assessor left join fetch s.blocks where s.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultFeedbackAndTextBlocks(@Param("submissionId") Long submissionId);

    /**
     * Gets all open (without a result) TextSubmissions which are submitted and loads all blocks, results, and participation
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks", "blocks.cluster", "result", "participation", "participation.submissions" })
    List<TextSubmission> findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.assessor", "blocks" })
    Optional<TextSubmission> findByResult_Id(Long resultId);

    /**
     * Gets all TextSubmissions which are submitted and loads all blocks
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks" })
    List<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(Long exerciseId);

    /**
     * Gets all TextSubmissions which are submitted, with matching and loads all blocks
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    List<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrueAndLanguage(Long exerciseId, Language language);
}
