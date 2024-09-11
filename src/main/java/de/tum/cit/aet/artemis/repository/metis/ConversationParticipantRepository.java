package de.tum.cit.aet.artemis.repository.metis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.metis.ConversationParticipant;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ConversationParticipant entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ConversationParticipantRepository extends ArtemisJpaRepository<ConversationParticipant, Long> {

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id IN :userIds
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationIdAndUserIds(@Param("conversationId") Long conversationId, @Param("userIds") Set<Long> userIds);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationId(@Param("conversationId") Long conversationId);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
                LEFT JOIN FETCH conversationParticipant.user user
                LEFT JOIN FETCH user.groups
                LEFT JOIN FETCH user.authorities
            WHERE conversationParticipant.conversation.id = :conversationId
            """)
    Set<ConversationParticipant> findConversationParticipantsWithUserGroupsByConversationId(@Param("conversationId") Long conversationId);

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
        return getValueElseThrow(findConversationParticipantByConversationIdAndUserId(conversationId, userId));
    }

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id = :userId
                AND conversationParticipant.isModerator = TRUE
            """)
    Optional<ConversationParticipant> findModeratorConversationParticipantByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

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
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id <> :senderId
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
            """)
    void incrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * Decrement unreadMessageCount field of ConversationParticipant
     *
     * @param senderId       userId of the sender of the message(Post)
     * @param conversationId conversationId id of the conversation with participants
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount - 1
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id <> :senderId
                AND conversationParticipant.unreadMessagesCount > 0
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
            """)
    void decrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);
}
