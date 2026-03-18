package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceSetting entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface ScienceSettingRepository extends ArtemisJpaRepository<ScienceSetting, Long> {

    Set<ScienceSetting> findAllByUserId(long userId);

    @Transactional // ok because of delete
    void deleteAllByUserId(long userId);
}
