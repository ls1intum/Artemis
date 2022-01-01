package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingSubmissionRepository extends JpaRepository<ModelingSubmission, Long> {

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.results r left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findByIdWithEagerResult(@Param("submissionId") Long submissionId);

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.results r left join fetch r.feedbacks left join fetch r.assessor where submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findByIdWithEagerResultAndAssessorAndFeedback(@Param("submissionId") Long submissionId);

    /**
     * Load the modeling submission with the given id together with its result, the feedback list of the result, the assessor of the result, its participation and all results of
     * the participation.
     *
     * @param submissionId the id of the modeling submission that should be loaded from the database
     * @return the modeling submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor", "participation", "participation.results" })
    Optional<ModelingSubmission> findWithResultsFeedbacksAssessorAndParticipationResultsById(Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    Optional<ModelingSubmission> findWithEagerResultById(Long submissionId);

    /**
     * Load all modeling submissions with the given ids. Load every submission together with its result, the feedback list of the result, the assessor of the result, its
     * participation and all results of the participation.
     *
     * @param submissionIds the ids of the modeling submissions that should be loaded from the database
     * @return the list of modeling submissions with their results, the feedback list of the results, the assessor of the results, their participation and all results of the
     *         participations
     */
    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.assessor", "participation", "participation.results" })
    List<ModelingSubmission> findWithEagerResultsFeedbacksAssessorAndParticipationResultsByIdIn(Collection<Long> submissionIds);

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.results r left join fetch r.feedbacks where submission.participation.exercise.id = :#{#exerciseId} and submission.submitted = true")
    List<ModelingSubmission> findSubmittedByExerciseIdWithEagerResultsAndFeedback(@Param("exerciseId") Long exerciseId);

    @Query("select distinct submission from ModelingSubmission submission left join fetch submission.results r left join fetch r.feedbacks where submission.exampleSubmission = true and submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findExampleSubmissionByIdWithEagerResult(@Param("submissionId") Long submissionId);

    /**
     * @param courseId  the course we are interested in
     * @param submitted boolean to check if an exercise has been submitted or not
     * @return number of submissions belonging to courseId with submitted status
     */
    long countByParticipation_Exercise_Course_IdAndSubmitted(Long courseId, boolean submitted);

    /**
     * Get the modeling submission with the given id from the database. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the modeling submission with the given id
     */
    default ModelingSubmission findByIdElseThrow(Long submissionId) {
        return findById(submissionId).orElseThrow(() -> new EntityNotFoundException("Modeling Submission", submissionId));
    }

    /**
     * Get the modeling submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the modeling submission with the given id
     */
    default ModelingSubmission findByIdWithEagerResultAndFeedbackElseThrow(Long submissionId) {
        return findByIdWithEagerResultAndAssessorAndFeedback(submissionId).orElseThrow(() -> new EntityNotFoundException("Modeling Submission", submissionId));
    }

    /**
     * Get the modeling submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the result,
     * its participation and all results of the participation. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the modeling submission with the given id
     */
    default ModelingSubmission findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(Long submissionId) {
        return findWithResultsFeedbacksAssessorAndParticipationResultsById(submissionId).orElseThrow(() -> new EntityNotFoundException("Modeling Submission", submissionId));
    }
}
