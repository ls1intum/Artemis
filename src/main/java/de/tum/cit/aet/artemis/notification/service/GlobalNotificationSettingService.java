package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class GlobalNotificationSettingService {

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    public GlobalNotificationSettingService(GlobalNotificationSettingRepository globalNotificationSettingRepository) {
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
    }

    /**
     * Checks whether an e-mail of the given type may be sent to the user.
     * <p>
     * A setting the user chose decides. Without one, the answer is yes for everybody except test accounts, for which a setting
     * that was never configured means that nothing is sent (see {@link TestAccountEmailService}).
     *
     * @param user the recipient
     * @param type the type of notification
     * @return true if the notification is enabled
     */
    public boolean isNotificationEnabled(User user, GlobalNotificationType type) {
        return isNotificationEnabled(user.getId(), user.isTestUser(), type);
    }

    /**
     * Checks whether an e-mail of the given type may be sent to the user, for a caller that holds the id of the user and whether
     * the user is a test user, but not the entity. See {@link #isNotificationEnabled(User, GlobalNotificationType)} for what decides.
     *
     * @param userId   the id of the recipient
     * @param testUser whether the recipient is a test user
     * @param type     the type of notification
     * @return true if the notification is enabled
     */
    public boolean isNotificationEnabled(long userId, boolean testUser, GlobalNotificationType type) {
        return globalNotificationSettingRepository.findByUserIdAndNotificationType(userId, type).map(GlobalNotificationSetting::getEnabled).orElseGet(() -> !testUser);
    }

    /**
     * Returns a map of email notification settings for a given user.
     * Each entry in the map corresponds to an {@link GlobalNotificationType}, with the key being the enum's {@code name()},
     * and the value indicating whether notifications of that type are enabled.
     * If a setting is not explicitly defined for a type, it defaults to {@code true}, except for test users, where it defaults
     * to {@code false}.
     *
     * @param user the user whose notification settings should be retrieved
     * @return a map of {@link GlobalNotificationType} names to their enabled/disabled status
     */
    public Map<String, Boolean> getAllSettingsAsMap(User user) {
        var settings = globalNotificationSettingRepository.findByUserId(user.getId());
        boolean enabledByDefault = !user.isTestUser();
        Map<String, Boolean> result = new HashMap<>();
        for (GlobalNotificationType type : GlobalNotificationType.values()) {
            boolean enabled = settings.stream().filter(setting -> setting.getNotificationType() == type).findFirst().map(GlobalNotificationSetting::getEnabled)
                    .orElse(enabledByDefault);
            result.put(type.name(), enabled);
        }
        return result;
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
