package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SolutionProgrammingExerciseParticipationRepository extends JpaRepository<SolutionProgrammingExerciseParticipation, Long> {

    @EntityGraph(attributePaths = { "results", "programmingExercise" })
    @Query("select p from SolutionProgrammingExerciseParticipation p where p.buildPlanId = :#{#buildPlanId}")
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanIdWithResults(@Param("buildPlanId") String buildPlanId);

    @EntityGraph(attributePaths = { "results", "submissions" })
    Optional<SolutionProgrammingExerciseParticipation> findWithEagerResultsAndSubmissionsByProgrammingExerciseId(Long exerciseId);

    Optional<SolutionProgrammingExerciseParticipation> findByProgrammingExerciseId(Long programmingExerciseId);
}
