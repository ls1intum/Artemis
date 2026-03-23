package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO;
import de.tum.cit.aet.artemis.core.domain.User;
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
    @Modifying
    void deleteAllByUserId(long userId);

    @Transactional
    default List<ScienceSetting> replaceScienceSettingsForUser(User user, ScienceSettingDTO[] scienceSettings) {
        deleteAllByUserId(user.getId());
        flush();
        List<ScienceSetting> scienceSettingList = Arrays.stream(scienceSettings).map(dto -> new ScienceSetting(user, dto.settingId().trim(), dto.active())).toList();
        return saveAll(scienceSettingList);
    }
}
