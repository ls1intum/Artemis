package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.repository.EmailNotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
@Transactional
public class EmailNotificationSettingService {

    private final EmailNotificationSettingRepository emailNotificationSettingRepository;

    public EmailNotificationSettingService(EmailNotificationSettingRepository emailNotificationSettingRepository) {
        this.emailNotificationSettingRepository = emailNotificationSettingRepository;
    }

    /**
     * Retrieves all email notification settings for a given user.
     *
     * @param userId the ID of the user
     * @return a list of email notification settings
     */
    public List<EmailNotificationSetting> getUserSettings(Long userId) {
        return emailNotificationSettingRepository.findByUserId(userId);
    }

    /**
     * Retrieves a specific email notification setting for a user and notification type.
     *
     * @param userId           the ID of the user
     * @param notificationType the type of notification
     * @return an optional containing the setting if it exists
     */
    public Optional<EmailNotificationSetting> getUserSetting(Long userId, EmailNotificationType notificationType) {
        return emailNotificationSettingRepository.findByUserIdAndNotificationType(userId, notificationType);
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

    /**
     * Checks whether a specific notification is enabled for a given user.
     * Defaults to true if no explicit setting exists.
     *
     * @param userId           the ID of the user
     * @param notificationType the type of notification
     * @return true if the notification is enabled or no setting exists, false otherwise
     */
    public boolean isNotificationEnabled(Long userId, EmailNotificationType notificationType) {
        // Default to true if no setting exists
        return emailNotificationSettingRepository.findByUserIdAndNotificationType(userId, notificationType).map(EmailNotificationSetting::getEnabled).orElse(true);
    }
}
