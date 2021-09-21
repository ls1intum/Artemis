package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationSetting;

/**
 * Spring Data repository for the NotificationOption entity.
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
}
