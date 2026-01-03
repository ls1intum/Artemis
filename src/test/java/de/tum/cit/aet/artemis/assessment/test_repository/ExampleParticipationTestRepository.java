package de.tum.cit.aet.artemis.assessment.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.repository.ExampleParticipationRepository;

@Lazy
@Repository
@Primary
public interface ExampleParticipationTestRepository extends ExampleParticipationRepository {

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<ExampleParticipation> findBySubmissionsId(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "submissions.results.feedbacks" })
    Set<ExampleParticipation> findAllWithSubmissionsResultsAndFeedbacksByExerciseId(long exerciseId);

    /**
     * Find all example participations with submissions, results, feedbacks and text blocks for a given exercise.
     * This is specifically for text exercises where we need to access the text blocks and feedbacks.
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

    /**
     * Find all example participations with submissions, results, feedbacks, grading instructions and grading criteria.
     * Used for tests that need to access the full grading instruction and criterion hierarchy.
     *
     * @param exerciseId the id of the exercise
     * @return set of example participations with all grading data loaded
     */
    @Query("""
            SELECT DISTINCT ep
            FROM ExampleParticipation ep
                LEFT JOIN FETCH ep.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.gradingInstruction gi
                LEFT JOIN FETCH gi.gradingCriterion
            WHERE ep.exercise.id = :exerciseId
            """)
    Set<ExampleParticipation> findAllWithSubmissionsResultsFeedbacksAndGradingInstructionsByExerciseId(@Param("exerciseId") long exerciseId);
}
