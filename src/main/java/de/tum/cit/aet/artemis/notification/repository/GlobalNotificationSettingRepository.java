package de.tum.cit.aet.artemis.notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;

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
     * Checks whether the user with the given login has explicitly switched at least one global notification on.
     *
     * @param login the login of the user
     * @return true if a setting of the user is enabled
     */
    @Query("""
            SELECT COUNT(setting) > 0
            FROM GlobalNotificationSetting setting
            WHERE setting.enabled = TRUE
                AND setting.userId IN (
                    SELECT user.id
                    FROM User user
                    WHERE user.login = :login
                )
            """)
    boolean existsEnabledSettingByUserLogin(@Param("login") String login);

    @Transactional // ok because of deletion
    @Modifying
    void deleteAllByUserId(Long userId);
}
