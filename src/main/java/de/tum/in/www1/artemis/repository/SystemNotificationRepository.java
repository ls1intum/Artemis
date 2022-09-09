package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.SystemNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    @Query("SELECT distinct notification FROM SystemNotification notification WHERE (notification.expireDate >= :now OR notification.expireDate IS NULL) ORDER BY notification.notificationDate ASC")
    List<SystemNotification> findAllActiveAndFutureSystemNotifications(@Param("now") ZonedDateTime now);
}
