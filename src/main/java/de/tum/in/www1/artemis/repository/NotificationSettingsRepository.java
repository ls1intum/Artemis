package de.tum.in.www1.artemis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.NotificationSettings;

/**
 * Spring Data repository for the NotificationSettings entity.
 */
@Repository
public interface NotificationSettingsRepository {

    @Query("""
            SELECT notificationSettings FROM NotificationSettings notificationSettings
            WHERE notificationSettings.user_id = :#{#id}
            """)
    Page<NotificationSettings> findAllNotificationSettingsForRecipientWithId(@Param("id") long id, Pageable pageable);
}
