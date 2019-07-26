package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TemplateProgrammingExerciseParticipation;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TemplateProgrammingExerciseParticipationRepository extends JpaRepository<TemplateProgrammingExerciseParticipation, Long> {

    @EntityGraph(attributePaths = "results")
    Optional<TemplateProgrammingExerciseParticipation> findByBuildPlanId(String buildPlanId);
}
