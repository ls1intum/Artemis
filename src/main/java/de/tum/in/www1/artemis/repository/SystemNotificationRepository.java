package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.SystemNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    @Query("SELECT distinct notification FROM SystemNotification notification where notification.notificationDate <= function('UTC_TIMESTAMP') and (function('UTC_TIMESTAMP') <= notification.expireDate OR notification.expireDate IS NULL) ORDER BY notification.notificationDate ASC")
    List<SystemNotification> findAllActiveSystemNotification();
}
