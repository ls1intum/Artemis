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

/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingSubmissionRepository extends JpaRepository<ModelingSubmission, Long> {

    @Query("SELECT DISTINCT submission FROM ModelingSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.assessor WHERE submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findWithEagerResultById(@Param("submissionId") Long submissionId);

    @Query("SELECT DISTINCT submission FROM ModelingSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.feedbacks LEFT JOIN FETCH r.assessor WHERE submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findWithEagerResultAndFeedbackById(@Param("submissionId") Long submissionId);

    /**
     * Load the modeling submission with the given id together with its result, the feedback list of the result, the assessor of the result, its participation and all results of
     * the participation.
     *
     * @param submissionId the id of the modeling submission that should be loaded from the database
     * @return the modeling submission with its result, the feedback list of the result, the assessor of the result, its participation and all results of the participation
     */
    @EntityGraph(type = LOAD, attributePaths = { "result", "result.feedbacks", "result.assessor", "participation", "participation.results" })
    Optional<ModelingSubmission> findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsById(Long submissionId);

    /**
     * Load all modeling submissions with the given ids. Load every submission together with its result, the feedback list of the result, the assessor of the result, its
     * participation and all results of the participation.
     *
     * @param submissionIds the ids of the modeling submissions that should be loaded from the database
     * @return the list of modeling submissions with their results, the feedback list of the results, the assessor of the results, their participation and all results of the
     *         participations
     */
    @EntityGraph(type = LOAD, attributePaths = { "result", "result.feedbacks", "result.assessor", "participation", "participation.results" })
    List<ModelingSubmission> findAllWithEagerResultAndFeedbackAndAssessorAndParticipationResultsByIdIn(Collection<Long> submissionIds);

    @Query("SELECT DISTINCT submission FROM ModelingSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.feedbacks WHERE submission.participation.exercise.id = :#{#exerciseId} and submission.submitted = true")
    List<ModelingSubmission> findAllWithEagerResultsAndFeedbackByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT DISTINCT submission FROM ModelingSubmission submission LEFT JOIN FETCH submission.result r LEFT JOIN FETCH r.feedbacks WHERE submission.exampleSubmission = true and submission.id = :#{#submissionId}")
    Optional<ModelingSubmission> findExampleSubmissionByIdWithEagerResult(@Param("submissionId") Long submissionId);

    /**
     * @param courseId  the course we are interested in
     * @param submitted boolean to check if an exercise has been submitted or not
     * @return number of submissions belonging to courseId with submitted status
     */
    long countByParticipationExerciseCourseIdAndSubmitted(Long courseId, boolean submitted);
}
