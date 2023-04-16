package de.tum.in.www1.artemis.repository.metis.conversation;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.ConversationNotification;

@Repository
public interface ConversationNotificationRepository extends JpaRepository<ConversationNotification, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByMessageId(Long messageId);
}
