package de.tum.in.www1.artemis.repository.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.CompetencyJOL;

/**
 * Spring Data JPA repository for the {@link CompetencyJOL} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyJOLRepository extends JpaRepository<CompetencyJOL, Long> {

    Optional<CompetencyJOL> findByCompetencyIdAndUserId(long competencyId, long userId);
}
