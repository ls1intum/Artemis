package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceSetting entity.
 */
@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
@Repository
public interface ScienceSettingRepository extends ArtemisJpaRepository<ScienceSetting, Long> {

    Set<ScienceSetting> findAllByUserId(long userId);
}
