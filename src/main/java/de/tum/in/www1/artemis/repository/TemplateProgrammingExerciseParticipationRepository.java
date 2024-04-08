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

import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TemplateProgrammingExerciseParticipationRepository extends JpaRepository<TemplateProgrammingExerciseParticipation, Long> {

    @Query("""
            SELECT p
            FROM TemplateProgrammingExerciseParticipation p
                LEFT JOIN FETCH p.results r
                LEFT JOIN FETCH p.programmingExercise e
            WHERE p.buildPlanId = :buildPlanId
            """)
    Optional<TemplateProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(long exerciseId);

    default TemplateProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(long exerciseId) {
        return findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExerciseParticipation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submission.results" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerSubmissionsAndSubmissionResultsByProgrammingExerciseId(long exerciseId);

    Optional<TemplateProgrammingExerciseParticipation> findByProgrammingExerciseId(long programmingExerciseId);

    default TemplateProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Template Programming Exercise Participation", programmingExerciseId));
    }
}
