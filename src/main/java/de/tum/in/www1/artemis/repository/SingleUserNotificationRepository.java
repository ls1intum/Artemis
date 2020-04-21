package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT userNotification FROM SingleUserNotification userNotification WHERE userNotification.notificationDate > userNotification.recipient.lastNotificationRead AND userNotification.recipient.login = :#{#login}")
    List<Notification> findAllByLogin(@Param("login") String login);

}
