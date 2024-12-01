package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceSetting entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ScienceSettingRepository extends ArtemisJpaRepository<ScienceSetting, Long> {

    Set<ScienceSetting> findAllByUserId(long userId);
}
