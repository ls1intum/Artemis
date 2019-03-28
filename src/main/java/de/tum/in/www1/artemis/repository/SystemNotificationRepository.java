package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    @Query("SELECT distinct notification FROM SystemNotification notification where notification.notificationDate <= utc_timestamp and (utc_timestamp <= notification.expireDate OR notification.expireDate IS NULL) ORDER BY notification.notificationDate ASC")
    List<SystemNotification> findAllActiveSystemNotification();
}

