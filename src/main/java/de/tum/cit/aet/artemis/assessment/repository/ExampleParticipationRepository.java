package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ExampleParticipation entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExampleParticipationRepository extends ArtemisJpaRepository<ExampleParticipation, Long> {

    Long countAllByExerciseId(long exerciseId);

    Set<ExampleParticipation> findAllByExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results" })
    Set<ExampleParticipation> findAllWithSubmissionsAndResultsByExerciseId(long exerciseId);

    /**
     * Find all example participations with submissions, results, and feedbacks for a given exercise.
     * This is for modeling exercises where we need feedbacks for import.
     *
     * @param exerciseId the id of the exercise
     * @return set of example participations with submissions, results and feedbacks
     */
    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE ep.exercise.id = :exerciseId
            """)
    Set<ExampleParticipation> findAllWithSubmissionsResultsAndFeedbacksByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Find all example participations with submissions, results, feedbacks and text blocks for a given exercise.
     * This is specifically for text exercises where we need to access the text blocks and feedbacks for import.
     *
     * @param exerciseId the id of the exercise
     * @return set of example participations with submissions, results, feedbacks and text blocks
     */
    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH TREAT(s AS TextSubmission).blocks
            WHERE ep.exercise.id = :exerciseId
            """)
    Set<ExampleParticipation> findAllWithSubmissionsResultsFeedbacksAndTextBlocksByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.tutorParticipations
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
            WHERE ep.id = :exampleParticipationId
            """)
    Optional<ExampleParticipation> findByIdWithResultsAndTutorParticipations(@Param("exampleParticipationId") long exampleParticipationId);

    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
            WHERE ep.id = :exampleParticipationId
            """)
    Optional<ExampleParticipation> findByIdWithResultsAndFeedback(@Param("exampleParticipationId") long exampleParticipationId);

    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
            WHERE s.id = :submissionId
            """)
    Optional<ExampleParticipation> findWithResultsBySubmissionId(@Param("submissionId") long submissionId);

    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH ep.exercise e
                LEFT JOIN FETCH e.gradingCriteria
            WHERE ep.id = :exampleParticipationId
            """)
    Optional<ExampleParticipation> findWithSubmissionResultExerciseGradingCriteriaById(@Param("exampleParticipationId") long exampleParticipationId);

    /**
     * Given the id of an example participation, it returns the feedback of the linked submission's result, if any
     *
     * @param exampleParticipationId the id of the example participation we want to retrieve
     * @return set of feedback for an example participation
     */
    default Set<Feedback> getFeedbackForExampleParticipation(long exampleParticipationId) {
        var exampleParticipation = getValueElseThrow(findByIdWithResultsAndFeedback(exampleParticipationId), exampleParticipationId);
        var submission = exampleParticipation.getSubmission();

        if (submission == null) {
            return Set.of();
        }

        Result result = submission.getLatestResult();

        // result.isExampleResult() can have 3 values: null, false, true. We return if it is not true
        if (result == null || !Boolean.TRUE.equals(result.isExampleResult())) {
            return Set.of();
        }

        return result.getFeedbacks();
    }

    default ExampleParticipation findByIdWithEagerResultAndFeedbackElseThrow(long exampleParticipationId) {
        return getValueElseThrow(findByIdWithResultsAndFeedback(exampleParticipationId), exampleParticipationId);
    }

    default ExampleParticipation findBySubmissionIdWithResultsElseThrow(long submissionId) {
        return getValueElseThrow(findWithResultsBySubmissionId(submissionId), submissionId);
    }
}
