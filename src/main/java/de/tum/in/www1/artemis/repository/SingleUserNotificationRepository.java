package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SingleUserNotificationRepository extends JpaRepository<SingleUserNotification, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteByRecipientId(long userId);
}
