package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SingleUserNotificationRepository extends ArtemisJpaRepository<SingleUserNotification, Long> {
}
