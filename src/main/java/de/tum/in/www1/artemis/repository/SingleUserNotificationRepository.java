package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<Notification, Long> {
}
