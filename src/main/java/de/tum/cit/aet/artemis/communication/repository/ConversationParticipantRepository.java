package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ConversationParticipant entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    // Mark a message and all subsequent messages as unread
    @Transactional
    @Modifying
    @Query("""
                UPDATE ConversationParticipant cp
                SET cp.unreadMessagesCount = (
                    SELECT COUNT(p) FROM Post p
                    WHERE p.conversation.id = :conversationId
                    AND p.creationDate >= :messageDate
                    AND p.author.id <> :userId
                ), cp.lastRead = :lastRead
                WHERE cp.conversation.id = :conversationId
                AND cp.user.id = :userId
            """)
    void markFromMessageAsUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("messageDate") ZonedDateTime messageDate,
            @Param("lastRead") ZonedDateTime lastRead);

    @Async
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant p
            SET p.lastRead = :now, p.unreadMessagesCount = 0
            WHERE p.user.id = :userId
                AND p.conversation.id IN :conversationIds
            """)
    void updateMultipleLastReadAsync(@Param("userId") Long userId, @Param("conversationIds") List<Long> conversationIds, @Param("now") ZonedDateTime now);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("""
            SELECT DISTINCT conversationParticipant.conversation.id
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.user.id = :userId
                AND conversationParticipant.conversation.course.id = :courseId
            """)
    List<Long> findConversationIdsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("""
            SELECT COUNT(DISTINCT conversation.id)
            FROM Conversation conversation
                LEFT JOIN conversation.conversationParticipants conversationParticipant
            WHERE conversation.id IN :conversationIds
                AND conversation.course.id = :courseId
                AND (
                    (conversationParticipant.user.id = :userId)
                    OR (TYPE(conversation) = Channel AND TREAT(conversation AS Channel).isCourseWide = TRUE)
                )
            """)
    long countAccessibleConversations(@Param("conversationIds") Collection<Long> conversationIds, @Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Verifies that the user has access to all specified conversations.
     * Throws AccessForbiddenException if one or more conversations are not accessible.
     *
     * @param conversationIds collection of conversation IDs
     * @param userId          ID of the user
     * @param courseId        ID of the course
     * @throws AccessForbiddenException if access is denied to one or more conversations
     */
    default void userHasAccessToAllConversationsElseThrow(Collection<Long> conversationIds, Long userId, Long courseId) {
        long accessibleCount = countAccessibleConversations(conversationIds, userId, courseId);
        if (accessibleCount != conversationIds.size()) {
            if (conversationIds.size() == 1) {
                throw new AccessForbiddenException("Conversation", conversationIds.iterator().next());
            }
            throw new AccessForbiddenException("Conversation", conversationIds);
        }
    }

    Optional<ConversationParticipant> findConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * Finds all conversation participations for a user for GDPR data export.
     *
     * @param userId the ID of the user
     * @return list of all conversation participations for the user
     */
    @Query("""
            SELECT cp
            FROM ConversationParticipant cp
            LEFT JOIN FETCH cp.conversation c
            LEFT JOIN FETCH c.course
            WHERE cp.user.id = :userId
            ORDER BY c.course.title, c.creationDate DESC
            """)
    List<ConversationParticipant> findAllByUserIdWithConversationAndCourse(@Param("userId") Long userId);

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
                AND conversationParticipant.isMuted = FALSE
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
