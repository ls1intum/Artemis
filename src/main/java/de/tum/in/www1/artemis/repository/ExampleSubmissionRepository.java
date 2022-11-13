package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ExampleSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExampleSubmissionRepository extends JpaRepository<ExampleSubmission, Long> {

    Long countAllByExerciseId(long exerciseId);

    Set<ExampleSubmission> findAllByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results" })
    Set<ExampleSubmission> findAllWithResultByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results" })
    @Query("""
            SELECT DISTINCT exampleSubmission
            FROM ExampleSubmission exampleSubmission
                LEFT JOIN FETCH exampleSubmission.tutorParticipations
            WHERE exampleSubmission.id = :exampleSubmissionId
            """)
    Optional<ExampleSubmission> findByIdWithResultsAndTutorParticipations(@Param("exampleSubmissionId") long exampleSubmissionId);

    @Query("""
            SELECT DISTINCT exampleSubmission
            FROM ExampleSubmission exampleSubmission
                LEFT JOIN FETCH exampleSubmission.submission s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE exampleSubmission.id = :exampleSubmissionId
            """)
    Optional<ExampleSubmission> findByIdWithResultsAndFeedback(@Param("exampleSubmissionId") long exampleSubmissionId);

    Optional<ExampleSubmission> findBySubmissionId(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results" })
    Optional<ExampleSubmission> findWithResultsBySubmissionId(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submission", "submission.results", "submission.results.feedbacks", "exercise", "exercise.gradingCriteria" })
    Optional<ExampleSubmission> findWithSubmissionResultExerciseGradingCriteriaById(@Param("exampleSubmissionId") long exampleSubmissionId);

    /**
     * Given the id of an example submission, it returns the results of the linked submission, if any
     *
     * @param exampleSubmissionId the id of the example submission we want to retrieve
     * @return list of feedback for an example submission
     */
    default List<Feedback> getFeedbackForExampleSubmission(long exampleSubmissionId) {
        var exampleSubmission = findByIdWithResultsAndFeedback(exampleSubmissionId).orElseThrow(() -> new EntityNotFoundException("Example Submission", exampleSubmissionId));
        var submission = exampleSubmission.getSubmission();

        if (submission == null) {
            return List.of();
        }

        Result result = submission.getLatestResult();

        // result.isExampleResult() can have 3 values: null, false, true. We return if it is not true
        if (result == null || !Boolean.TRUE.equals(result.isExampleResult())) {
            return List.of();
        }

        return result.getFeedbacks();
    }

    default ExampleSubmission findByIdWithEagerResultAndFeedbackElseThrow(long exampleSubmissionId) {
        return findByIdWithResultsAndFeedback(exampleSubmissionId).orElseThrow(() -> new EntityNotFoundException("Example Submission", exampleSubmissionId));
    }

    default ExampleSubmission findBySubmissionIdWithResultsElseThrow(long submissionId) {
        return findWithResultsBySubmissionId(submissionId).orElseThrow(() -> new EntityNotFoundException("Submission", submissionId));
    }

}
