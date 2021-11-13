package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationSetting;

/**
 * Spring Data repository for the NotificationSetting entity.
 */
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    /**
     * Finds all notification settings for a given user
     * @param userId of the given user
     * @return the set of all notification settings of the given user that were saved in the db
     */
    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
            LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id = :#{#userId}
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientWithId(@Param("userId") long userId);

    /**
     * Delete all settings that belong to the given user
     * @param userId is the id of the user whose settings should be deleted
     */
    // void deleteAllNotificationSettingsForRecipientWithId(@Param("userId") long userId);
    void deleteByUser_Id(long userId);
}
