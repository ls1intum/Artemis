package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.GuidedTourSetting;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the GuidedTourSetting entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface GuidedTourSettingsRepository extends ArtemisJpaRepository<GuidedTourSetting, Long> {

}
