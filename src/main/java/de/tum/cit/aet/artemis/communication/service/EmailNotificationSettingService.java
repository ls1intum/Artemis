package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.repository.EmailNotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
public class EmailNotificationSettingService {

    private final EmailNotificationSettingRepository emailNotificationSettingRepository;

    public EmailNotificationSettingService(EmailNotificationSettingRepository emailNotificationSettingRepository) {
        this.emailNotificationSettingRepository = emailNotificationSettingRepository;
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
    public EmailNotificationSetting createOrUpdateSetting(User user, EmailNotificationType notificationType, boolean enabled) {
        Optional<EmailNotificationSetting> existingSetting = emailNotificationSettingRepository.findByUserIdAndNotificationType(user.getId(), notificationType);

        EmailNotificationSetting setting;
        if (existingSetting.isPresent()) {
            setting = existingSetting.get();
            setting.setEnabled(enabled);
        }
        else {
            setting = new EmailNotificationSetting();
            setting.setUser(user);
            setting.setNotificationType(notificationType);
            setting.setEnabled(enabled);
        }

        return emailNotificationSettingRepository.save(setting);
    }
}
