package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.SolutionProgrammingExerciseParticipation;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SolutionProgrammingExerciseParticipationRepository extends JpaRepository<SolutionProgrammingExerciseParticipation, Long> {

    @EntityGraph(attributePaths = "results")
    Optional<SolutionProgrammingExerciseParticipation> findByBuildPlanId(String buildPlanId);
}
