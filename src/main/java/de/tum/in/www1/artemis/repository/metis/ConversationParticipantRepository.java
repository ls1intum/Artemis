package de.tum.in.www1.artemis.repository.metis;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.UserConversationWebSocketView;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ConversationParticipant entity.
 */
@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND conversationParticipant.user.id in :#{#userIds}
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationIdAndUserIds(Long conversationId, Set<Long> userIds);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            """)
    Set<ConversationParticipant> findConversationParticipantByConversationId(@Param("conversationId") Long conversationId);

    @Query("""
            SELECT NEW de.tum.in.www1.artemis.domain.UserConversationWebSocketView (
                u.login,
                CASE WHEN cp.isHidden THEN true ELSE false END
            )
            FROM ConversationParticipant cp
            JOIN cp.user u
            WHERE cp.conversation.id = :#{#conversationId}
            """)
    Set<UserConversationWebSocketView> findWebSocketRecipientsForConversation(@Param("conversationId") Long conversationId);

    @Async
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant p
            SET p.lastRead = :now, p.unreadMessagesCount = 0
            WHERE p.user.id = :userId
                AND p.conversation.id = :conversationId
            """)
    void updateLastReadAsync(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("now") ZonedDateTime now);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    default ConversationParticipant findConversationParticipantByConversationIdAndUserIdElseThrow(Long conversationId, Long userId) {
        return this.findConversationParticipantByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation participant not found!"));
    }

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND conversationParticipant.user.id = :#{#userId}
            AND conversationParticipant.isModerator = true
            """)
    Optional<ConversationParticipant> findModeratorConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    Integer countByConversationId(Long conversationId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByConversationId(Long conversationId);

    /**
     * Increment unreadMessageCount field of ConversationParticipant
     *
     * @param senderId       userId of the sender of the message(Post)
     * @param conversationId conversationId id of the conversation with participants
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount + 1
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND (conversationParticipant.user.id <> :#{#senderId})
            AND conversationParticipant.unreadMessagesCount IS NOT null
            """)
    void incrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * Decrement unreadMessageCount field of ConversationParticipant
     *
     * @param senderId       userId of the sender of the message(Post)
     * @param conversationId conversationId id of the conversation with participants
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount - 1
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND (conversationParticipant.user.id <> :#{#senderId})
            AND conversationParticipant.unreadMessagesCount > 0
            AND conversationParticipant.unreadMessagesCount IS NOT null
            """)
    void decrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);
}
