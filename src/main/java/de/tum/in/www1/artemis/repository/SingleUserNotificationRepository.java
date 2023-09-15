package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<SingleUserNotification, Long> {
}
