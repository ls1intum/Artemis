package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class GlobalNotificationSettingService {

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    public GlobalNotificationSettingService(GlobalNotificationSettingRepository globalNotificationSettingRepository) {
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
    }

    /**
     * Creates or updates a user's email notification setting.
     * If the setting exists, it will be updated; otherwise, a new one will be created.
     *
     * @param user             the user entity
     * @param notificationType the type of notification
     * @param enabled          whether the notification is enabled
     * @return the saved email notification setting
     */
    public GlobalNotificationSetting createOrUpdateSetting(User user, GlobalNotificationType notificationType, boolean enabled) {
        Optional<GlobalNotificationSetting> existingSetting = globalNotificationSettingRepository.findByUserIdAndNotificationType(user.getId(), notificationType);

        GlobalNotificationSetting setting;
        if (existingSetting.isPresent()) {
            setting = existingSetting.get();
            setting.setEnabled(enabled);
        }
        else {
            setting = new GlobalNotificationSetting();
            setting.setUserId(user.getId());
            setting.setNotificationType(notificationType);
            setting.setEnabled(enabled);
        }

        return globalNotificationSettingRepository.save(setting);
    }

    /**
     * Deletes all the global notifications of a user
     *
     * @param userId the ID of the user.
     */
    public void deleteAllByUserId(Long userId) {
        globalNotificationSettingRepository.deleteAllByUserId(userId);
    }
}
