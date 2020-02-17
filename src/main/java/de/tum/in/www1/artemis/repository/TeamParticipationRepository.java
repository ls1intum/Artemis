package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.participation.TeamParticipation;

/**
 * Spring Data JPA repository for the TeamParticipation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, Long> {

    Optional<TeamParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId);

}
