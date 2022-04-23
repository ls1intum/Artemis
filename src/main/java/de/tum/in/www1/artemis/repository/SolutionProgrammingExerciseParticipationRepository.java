package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

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
@SuppressWarnings("unused")
@Repository
public interface SolutionProgrammingExerciseParticipationRepository extends JpaRepository<SolutionProgrammingExerciseParticipation, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "results", "programmingExercise" })
    @Query("select p from SolutionProgrammingExerciseParticipation p where p.buildPlanId = :#{#buildPlanId}")
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "submissions", "submissions.results" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "results", "results.feedbacks", "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);

    default SolutionProgrammingExerciseParticipation findByProgrammingExerciseIdElseThrow(Long programmingExerciseId) {
        var optional = findByProgrammingExerciseId(programmingExerciseId);
        return optional.orElseThrow(() -> new EntityNotFoundException("Solution Programming Exercise Participation", programmingExerciseId));
    }
}
