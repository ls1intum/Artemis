package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<SingleUserNotification, Long> {
}
