package de.tum.cit.aet.artemis.atlas.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ScienceSettingService {

    private final ScienceSettingRepository scienceSettingRepository;

    public ScienceSettingService(ScienceSettingRepository scienceSettingRepository) {
        this.scienceSettingRepository = scienceSettingRepository;
    }

    /**
     * Replaces all science settings for the given user in one transaction.
     *
     * @param user            the user whose settings should be replaced
     * @param scienceSettings the settings that should be persisted
     * @return the persisted settings
     */
    @Transactional
    public List<ScienceSetting> replaceScienceSettingsForUser(User user, ScienceSettingDTO[] scienceSettings) {
        scienceSettingRepository.deleteAllByUserId(user.getId());
        scienceSettingRepository.flush();
        List<ScienceSetting> scienceSettingList = Arrays.stream(scienceSettings).map(dto -> new ScienceSetting(user, dto.settingId().trim(), dto.active())).toList();
        return scienceSettingRepository.saveAll(scienceSettingList);
    }
}
