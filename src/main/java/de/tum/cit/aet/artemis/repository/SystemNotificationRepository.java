package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SystemNotificationRepository extends ArtemisJpaRepository<SystemNotification, Long> {

    @Query("""
            SELECT DISTINCT notification
            FROM SystemNotification notification
            WHERE notification.expireDate >= :now
                OR notification.expireDate IS NULL
            ORDER BY notification.notificationDate ASC
            """)
    List<SystemNotification> findAllActiveAndFutureSystemNotifications(@Param("now") ZonedDateTime now);
}
