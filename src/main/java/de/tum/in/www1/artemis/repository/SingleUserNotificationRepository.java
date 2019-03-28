package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select userNotification from SingleUserNotification userNotification where userNotification.notificationDate > userNotification.recipient.lastNotificationRead AND userNotification.recipient.login = ?#{principal.username}")
    List<Notification> findAllNewNotificationsForCurrentUser();

}
