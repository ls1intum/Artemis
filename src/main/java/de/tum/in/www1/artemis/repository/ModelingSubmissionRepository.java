package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;

/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@Repository
public interface ModelingSubmissionRepository extends GenericSubmissionRepository<ModelingSubmission> {

    /**
     * Load all modeling submissions with the given ids. Load every submission together with its result, the feedback list of the result, the assessor of the result, its
     * participation and all results of the participation.
     *
     * @param submissionIds the ids of the modeling submissions that should be loaded from the database
     * @return the list of modeling submissions with their results, the feedback list of the results, the assessor of the results, their participation and all results of the
     *         participations
     */
    @EntityGraph(attributePaths = { "result", "result.feedbacks", "result.assessor", "participation", "participation.results" })
    List<ModelingSubmission> findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsByIdIn(Collection<Long> submissionIds);

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.result r left join fetch r.feedbacks where submission.participation.exercise.id = :#{#exerciseId} and submission.submitted = true")
    List<ModelingSubmission> findSubmittedByExerciseIdWithEagerResultsAndFeedback(@Param("exerciseId") long exerciseId);

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.result r left join fetch r.feedbacks where submission.exampleSubmission = true and submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findExampleSubmissionByIdWithEagerResult(@Param("submissionId") long submissionId);
}
