package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

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
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    default TemplateProgrammingExerciseParticipation findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(Long exerciseId) {
        return findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exerciseId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExerciseParticipation", exerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "results.feedbacks.testCase", "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndTestCasesAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions" })
    Optional<TemplateProgrammingExerciseParticipation> findWithEagerSubmissionsByProgrammingExerciseId(Long exerciseId);

    Optional<TemplateProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);

    default TemplateProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(Long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Template Programming Exercise Participation", programmingExerciseId));
    }
}
