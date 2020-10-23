package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

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

    @Query("select distinct submission from TextSubmission submission left join fetch submission.participation participation left join fetch participation.exercise left join fetch submission.results result left join fetch result.assessor left join fetch result.feedbacks where submission.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerParticipationExerciseResultAssessor(@Param("submissionId") Long submissionId);

    /**
     * @param submissionId the submission id we are interested in
     * @return the submission with its feedback and assessor
     */
    @Query("select distinct s from TextSubmission s left join fetch s.results r left join fetch r.feedbacks left join fetch r.assessor where s.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerResultFeedback(@Param("submissionId") Long submissionId);

    @Query("select distinct s from TextSubmission s left join fetch s.blocks where s.id = :#{#submissionId}")
    Optional<TextSubmission> findByIdWithEagerTextBlocks(@Param("submissionId") Long submissionId);

    /**
     * Gets all open (without a result) TextSubmissions which are submitted and loads all blocks, results, and participation
     * @param exerciseId the Id of the exercise
     * @return List of Text Submissions
     */
    @Query("select distinct s from TextSubmission s left join fetch s.results r left join fetch s.participation p where p.exercise.id = :#{#exerciseId}")
    List<TextSubmission> findByParticipation_ExerciseIdAndResultsIsNullAndSubmittedIsTrue(Long exerciseId);

    @Query("select distinct s from TextSubmission s left join fetch s.blocks b left join fetch b.cluster left join fetch s.participation p where p.exercise.id = :#{#exerciseId}")
    List<TextSubmission> findByParticipation_ExerciseIdAndResultsIsNullAndSubmittedIsTrue_WithTextBlock(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.assessor" })
    Optional<TextSubmission> findByResults_Id(@Param("resultId") Long resultId);
}
