package de.tum.cit.aet.artemis.core.test_repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.repository.NotificationRepository;

@Repository
@Primary
public interface NotificationTestRepository extends NotificationRepository {

    @Query("""
            SELECT notification
            FROM Notification notification
                LEFT JOIN TREAT(notification AS SingleUserNotification).recipient recipient
            WHERE recipient.id = :recipientId
            """)
    List<Notification> findAllByRecipientId(long recipientId);
}
