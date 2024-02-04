package de.tum.in.www1.artemis.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GuidedTourSetting;

/**
 * Spring Data JPA repository for the GuidedTourSetting entity.
 */
@Profile("core")
@Repository
public interface GuidedTourSettingsRepository extends JpaRepository<GuidedTourSetting, Long> {

}
