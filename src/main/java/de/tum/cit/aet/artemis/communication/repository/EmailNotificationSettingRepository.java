package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface EmailNotificationSettingRepository extends ArtemisJpaRepository<EmailNotificationSetting, Long> {

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
            """)
    Set<EmailNotificationSetting> findByUserId(@Param("userId") long userId);

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
                AND setting.notificationType = :notificationType
            """)
    Optional<EmailNotificationSetting> findByUserIdAndNotificationType(@Param("userId") long userId, @Param("notificationType") @NotNull EmailNotificationType notificationType);

    /**
     * Checks whether a specific notification is enabled for a given user.
     * Defaults to true if no explicit setting exists.
     *
     * @param userId the ID of the user
     * @param type   the type of notification
     * @return true if the notification is enabled or no setting exists, false otherwise
     */
    default boolean isNotificationEnabled(long userId, EmailNotificationType type) {
        return findByUserIdAndNotificationType(userId, type).map(EmailNotificationSetting::getEnabled).orElse(true);
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
    default Map<String, Boolean> getAllSettingsAsMap(long userId) {
        Set<EmailNotificationSetting> settings = findByUserId(userId);
        Map<String, Boolean> result = new HashMap<>();
        for (EmailNotificationType type : EmailNotificationType.values()) {
            boolean enabled = settings.stream().filter(s -> s.getNotificationType() == type).findFirst().map(EmailNotificationSetting::getEnabled).orElse(true);
            result.put(type.name(), enabled);
        }
        return result;
    }

}
