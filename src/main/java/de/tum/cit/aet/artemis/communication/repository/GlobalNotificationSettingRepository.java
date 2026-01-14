package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
@Lazy
public interface GlobalNotificationSettingRepository extends ArtemisJpaRepository<GlobalNotificationSetting, Long> {

    @Query("""
            SELECT setting
            FROM GlobalNotificationSetting setting
            WHERE setting.userId = :userId
            """)
    Set<GlobalNotificationSetting> findByUserId(@Param("userId") long userId);

    @Query("""
            SELECT setting
            FROM GlobalNotificationSetting setting
            WHERE setting.userId = :userId
                AND setting.notificationType = :notificationType
            """)
    Optional<GlobalNotificationSetting> findByUserIdAndNotificationType(@Param("userId") long userId, @Param("notificationType") @NonNull GlobalNotificationType notificationType);

    /**
     * Checks whether a specific notification is enabled for a given user.
     * Defaults to true if no explicit setting exists.
     *
     * @param userId the ID of the user
     * @param type   the type of notification
     * @return true if the notification is enabled or no setting exists, false otherwise
     */
    default boolean isNotificationEnabled(long userId, GlobalNotificationType type) {
        return findByUserIdAndNotificationType(userId, type).map(GlobalNotificationSetting::getEnabled).orElse(true);
    }

    /**
     * Returns a map of email notification settings for a given user.
     * Each entry in the map corresponds to an {@link GlobalNotificationType}, with the key being the enum's {@code name()},
     * and the value indicating whether notifications of that type are enabled.
     * If a setting is not explicitly defined for a type, it defaults to {@code true}.
     *
     * @param userId the ID of the user whose notification settings should be retrieved
     * @return a map of {@link GlobalNotificationType} names to their enabled/disabled status
     */
    default Map<String, Boolean> getAllSettingsAsMap(long userId) {
        Set<GlobalNotificationSetting> settings = findByUserId(userId);
        Map<String, Boolean> result = new HashMap<>();
        for (GlobalNotificationType type : GlobalNotificationType.values()) {
            boolean enabled = settings.stream().filter(s -> s.getNotificationType() == type).findFirst().map(GlobalNotificationSetting::getEnabled).orElse(true);
            result.put(type.name(), enabled);
        }
        return result;
    }

    @Transactional
    @Modifying
    void deleteAllByUserId(Long userId);
}
