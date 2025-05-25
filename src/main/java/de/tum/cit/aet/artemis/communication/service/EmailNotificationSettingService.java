package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Retrieves all email notification settings for a given user.
     *
     * @param userId the ID of the user
     * @return a list of email notification settings
     */
    public List<EmailNotificationSetting> getUserSettings(long userId) {
        return emailNotificationSettingRepository.findByUserId(userId);
    }

    /**
     * Retrieves a specific email notification setting for a user and notification type.
     *
     * @param userId           the ID of the user
     * @param notificationType the type of notification
     * @return an optional containing the setting if it exists
     */
    public Optional<EmailNotificationSetting> getUserSetting(long userId, EmailNotificationType notificationType) {
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
    public boolean isNotificationEnabled(long userId, EmailNotificationType notificationType) {
        // Default to true if no setting exists
        return emailNotificationSettingRepository.findByUserIdAndNotificationType(userId, notificationType).map(EmailNotificationSetting::getEnabled).orElse(true);
    }

    /**
     * Returns a map of email notification settings for a given user.
     * Each entry in the map corresponds to an {@link EmailNotificationType}, with the key being the enum's {@code name()},
     * and the value indicating whether notifications of that type are enabled.
     * If a setting is not explicitly defined for a type, it defaults to {@code true}.
     *
     * @param userId the ID of the user whose notification settings should be retrieved
     * @return a map of {@link EmailNotificationType} names to their enabled/disabled status
     */
    public Map<String, Boolean> getAllUserSettingsAsMap(long userId) {
        List<EmailNotificationSetting> settings = getUserSettings(userId);
        Map<String, Boolean> result = new HashMap<>();
        for (EmailNotificationType type : EmailNotificationType.values()) {
            boolean enabled = settings.stream().filter(s -> s.getNotificationType() == type).findFirst().map(EmailNotificationSetting::getEnabled).orElse(true);
            result.put(type.name(), enabled);
        }
        return result;
    }
}
