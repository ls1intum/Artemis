package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SolutionProgrammingExerciseParticipationRepository extends JpaRepository<SolutionProgrammingExerciseParticipation, Long> {

    @Query("""
            SELECT p
            FROM SolutionProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH p.programmingExercise e
                LEFT JOIN FETCH e.templateParticipation
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions", "submissions.results" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default SolutionProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExerciseParticipation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(long exerciseId);

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(long programmingExerciseId);

    default SolutionProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Solution Programming Exercise Participation", programmingExerciseId));
    }
}
