package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data repository for the NotificationSetting entity.
 */
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
            LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id = :#{#userId}
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientWithId(@Param("userId") long userId);

    @EntityGraph(type = LOAD, attributePaths = { "user.groups", "user.authorities" })
    @Query("""
            SELECT setting
            FROM NotificationSetting setting
            WHERE setting.settingId = :#{#settingId}
                AND setting.email = TRUE
            """)
    Set<NotificationSetting> findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(@Param("settingId") String settingId);

    /**
     * @param settingId of the notification setting (e.g. NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY)
     * @return all users who enabled the provided email notification setting with eager groups and authorities
     */
    default Set<User> findAllUsersWhoEnabledSpecifiedEmailNotificationSettingWithEagerGroupsAndAuthorities(String settingId) {
        return findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(settingId).stream().map(NotificationSetting::getUser)
                .collect(Collectors.toSet());
    }
}
