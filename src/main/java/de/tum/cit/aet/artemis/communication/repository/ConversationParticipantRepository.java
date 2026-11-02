package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
                LEFT JOIN FETCH user.courseRoles
                LEFT JOIN FETCH user.authorities
            WHERE conversationParticipant.conversation.id = :conversationId
            """)
    Set<ConversationParticipant> findConversationParticipantsWithUserCourseRolesByConversationId(@Param("conversationId") Long conversationId);

    // Complete this transaction before returning read success: clients immediately reload authoritative counts.
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant p
            SET p.lastRead = :now, p.unreadMessagesCount = 0
            WHERE p.user.id = :userId
                AND p.conversation.id = :conversationId
            """)
    void updateLastRead(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("now") ZonedDateTime now);

    /**
     * Mark a message and all subsequent messages as unread.
     *
     * @param conversationId the id of the conversation
     * @param userId         the id of the user
     * @param messageDate    the creation date of the target message
     * @param lastRead       the lastRead timestamp to set (just before messageDate)
     */
    @Transactional // ok because of modifying query
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

    // Complete this transaction before returning read success: clients immediately reload authoritative counts.
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant p
            SET p.lastRead = :now, p.unreadMessagesCount = 0
            WHERE p.user.id = :userId
                AND p.conversation.id IN :conversationIds
            """)
    void updateMultipleLastRead(@Param("userId") Long userId, @Param("conversationIds") List<Long> conversationIds, @Param("now") ZonedDateTime now);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * Creates the participant of a user in a conversation unless the user already is one, in one statement.
     * <p>
     * Several requests create the same participant at once, for example the three message requests the client sends when a student opens a course-wide channel for the first
     * time. Checking inside the insert leaves no gap between the check and the insert, but two such statements that run at the same instant still both find the participant
     * missing, and the later one fails on the unique index {@code conversation_participant_uq}, which Hibernate logs at WARN. That is what the three requests of the client do,
     * because they run the same steps in lockstep. Call {@link #createIfAbsent} instead, which makes them wait for each other.
     * <p>
     * The participant is not a favorite, not hidden and not muted, like {@link ConversationParticipant#createWithDefaultValues}.
     *
     * @param conversationId      the id of the conversation
     * @param userId              the id of the user
     * @param isModerator         whether the user moderates the conversation
     * @param lastRead            the time the user read the conversation last
     * @param unreadMessagesCount the number of messages the user has not read
     * @return 1 if the participant was created, 0 if it existed or the conversation or the user does not exist
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            INSERT INTO ConversationParticipant (conversation, user, isModerator, isFavorite, isHidden, isMuted, lastRead, unreadMessagesCount)
            SELECT conversation, user, :isModerator, FALSE, FALSE, FALSE, :lastRead, :unreadMessagesCount
            FROM Conversation conversation, User user
            WHERE conversation.id = :conversationId
                AND user.id = :userId
                AND NOT EXISTS (
                    SELECT 1
                    FROM ConversationParticipant existing
                    WHERE existing.conversation = conversation
                        AND existing.user = user
                )
            """)
    int insertIfAbsent(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("isModerator") boolean isModerator,
            @Param("lastRead") ZonedDateTime lastRead, @Param("unreadMessagesCount") Long unreadMessagesCount);

    /**
     * Locks the row of a conversation until the end of the surrounding transaction. Every transaction that wants the lock waits until the one that holds it has committed.
     * The lock is released at the end of the transaction, so this only has an effect inside a transaction that also does the work, see {@link #lockAndInsertIfAbsent}.
     *
     * @param conversationId the id of the conversation
     * @return the id of the conversation, empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT conversation.id
            FROM Conversation conversation
            WHERE conversation.id = :conversationId
            """)
    Optional<Long> lockConversation(@Param("conversationId") Long conversationId);

    /**
     * Creates the participant like {@link #insertIfAbsent}, after taking the lock on the conversation in the same transaction.
     * <p>
     * Requests that create a participant of the same conversation at the same time queue up on the lock. The one that gets it inserts and commits, and the others then
     * run their statement in a new snapshot that contains the participant, so they insert nothing and no request fails on the unique index. The lock is per conversation and is
     * held only for the one insert, which happens once per user and conversation.
     *
     * @param conversationId      the id of the conversation
     * @param userId              the id of the user
     * @param isModerator         whether the user moderates the conversation
     * @param lastRead            the time the user read the conversation last
     * @param unreadMessagesCount the number of messages the user has not read
     * @return 1 if the participant was created, 0 if it existed or the conversation or the user does not exist
     */
    @Transactional // ok because the lock only holds for the insert if both are one transaction
    default int lockAndInsertIfAbsent(Long conversationId, Long userId, boolean isModerator, ZonedDateTime lastRead, Long unreadMessagesCount) {
        lockConversation(conversationId);
        return insertIfAbsent(conversationId, userId, isModerator, lastRead, unreadMessagesCount);
    }

    /**
     * Creates the participant of a user in a conversation unless the user already is one, also when another request creates it at the very same time.
     * <p>
     * Concurrent requests wait for each other on the lock of {@link #lockAndInsertIfAbsent}, so normally exactly one of them inserts and the others find the participant. If
     * the unique index refuses an insert anyway, or on MySQL (REPEATABLE READ) the database rolls back a deadlock victim, the other request created the participant. This
     * method counts both as "exists". The deadlock victim retries a few times, because the other request may not have committed yet when the victim looks. An integrity
     * violation while the participant does not exist, for example a missing user, is a real error.
     *
     * @param conversationId      the id of the conversation
     * @param userId              the id of the user
     * @param isModerator         whether the user moderates the conversation
     * @param lastRead            the time the user read the conversation last
     * @param unreadMessagesCount the number of messages the user has not read
     * @return true if this call created the participant, false if it existed already or another request created it
     */
    default boolean createIfAbsent(Long conversationId, Long userId, boolean isModerator, ZonedDateTime lastRead, Long unreadMessagesCount) {
        int maxAttempts = 3;
        for (int attempt = 1;; attempt++) {
            try {
                return lockAndInsertIfAbsent(conversationId, userId, isModerator, lastRead, unreadMessagesCount) == 1;
            }
            catch (DataIntegrityViolationException e) {
                if (existsByConversationIdAndUserId(conversationId, userId)) {
                    return false;
                }
                throw e;
            }
            catch (PessimisticLockingFailureException e) {
                if (existsByConversationIdAndUserId(conversationId, userId)) {
                    return false;
                }
                if (attempt >= maxAttempts) {
                    throw e;
                }
            }
        }
    }

    @Query("""
            SELECT DISTINCT conversationParticipant.conversation.id
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.user.id = :userId
                AND conversationParticipant.conversation.course.id = :courseId
            """)
    List<Long> findConversationIdsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Counts how many of the given conversations the user may read.
     * <p>
     * Written as an {@code EXISTS} rather than a join to the participants. Joining produces a row per participant, so
     * a course-wide channel with several thousand members multiplies the conversation out that many times and the
     * {@code DISTINCT} then sorts it all back down again - the cost grew with the size of the cohort rather than with
     * the number of conversations asked about, and this runs on every message fetch. {@code EXISTS} stops at the first
     * membership row and reads it through the unique index on (conversation_id, user_id).
     *
     * @param conversationIds the conversations to check
     * @param userId          the user whose access is being checked
     * @param courseId        the course the conversations must belong to
     * @param isTutor         whether the user may access channels before their release
     * @param now             the time used to check exercise and exam visibility
     * @return how many of the given conversations the user may read
     */
    @Query("""
            SELECT COUNT(conversation.id)
            FROM Conversation conversation
                LEFT JOIN Channel channel ON channel.id = conversation.id
                LEFT JOIN channel.exercise exercise
                LEFT JOIN channel.exam exam
            WHERE conversation.id IN :conversationIds
                AND conversation.course.id = :courseId
                AND (
                    (TYPE(conversation) = Channel AND TREAT(conversation AS Channel).isCourseWide = TRUE)
                    OR EXISTS (
                        SELECT 1
                        FROM ConversationParticipant participant
                        WHERE participant.conversation.id = conversation.id
                            AND participant.user.id = :userId
                    )
                )
                AND (:isTutor = TRUE OR (
                    (exercise.id IS NULL OR (exercise.exerciseGroup IS NULL AND (exercise.releaseDate IS NULL OR exercise.releaseDate < :now)))
                    AND (exercise.id IS NOT NULL OR exam.id IS NULL OR exam.visibleDate < :now)
                ))
            """)
    long countAccessibleConversations(@Param("conversationIds") Collection<Long> conversationIds, @Param("userId") Long userId, @Param("courseId") Long courseId,
            @Param("isTutor") boolean isTutor, @Param("now") ZonedDateTime now);

    /**
     * Verifies that the user has access to all specified conversations.
     * Throws AccessForbiddenException if one or more conversations are not accessible.
     *
     * @param conversationIds collection of conversation IDs
     * @param userId          ID of the user
     * @param courseId        ID of the course
     * @param isTutor         whether the user may access channels before their release
     * @throws AccessForbiddenException if access is denied to one or more conversations
     */
    default void userHasAccessToAllConversationsElseThrow(Collection<Long> conversationIds, Long userId, Long courseId, boolean isTutor) {
        long accessibleCount = countAccessibleConversations(conversationIds, userId, courseId, isTutor, ZonedDateTime.now());
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
     * Deletes all conversation participants (channel/conversation memberships and their read state) of every
     * conversation belonging to the given course. Used by the course reset to remove the per-user membership data while
     * the conversation/channel structure itself is preserved.
     *
     * @param courseId the id of the course whose conversation participants should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.course.id = :courseId
            """)
    void deleteAllByConversationCourseId(@Param("courseId") long courseId);

    /**
     * Increment the unreadMessagesCount field of the participants of a conversation who are eligible for an unread
     * message: everyone except the sender, whose {@code unreadMessagesCount} is not null and who has not muted the
     * conversation. Participants failing any of those conditions are left untouched.
     * <p>
     * Call this from the request that creates the message, not from a background thread. The read side
     * ({@code updateLastRead}) resets the counter to zero and nothing orders the two, so an increment running
     * asynchronously can land after a recipient's read and leave them with an unread message they already saw.
     *
     * @param conversationId id of the conversation with participants
     * @param senderId       userId of the sender of the message(Post)
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
     * Increment the unreadMessagesCount field of the given participants of a conversation, for a reply that only concerns
     * them: the author of a message and the users who replied to it. Like
     * {@link #incrementUnreadMessagesCountOfParticipants(Long, Long)}, participants whose {@code unreadMessagesCount} is
     * null or who muted the conversation are left untouched, and it has to be called from the request that creates the reply.
     * The sender of the reply must not be among the given users.
     *
     * @param conversationId id of the conversation with participants
     * @param userIds        ids of the users who are part of the thread the reply was written in
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount + 1
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id IN :userIds
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
                AND conversationParticipant.isMuted = FALSE
            """)
    void incrementUnreadMessagesCountOfParticipantsIn(@Param("conversationId") Long conversationId, @Param("userIds") Collection<Long> userIds);

    /**
     * Checks if the user has a conversation in the course whose unread count is positive and that is not muted. This covers what
     * the check of the posts of a course cannot see: a reply in a thread raises the count, but is no new top-level post.
     *
     * @param courseId the id of the course
     * @param userId   the id of the user
     * @param isTutor  whether the user may see channels before their release
     * @param now      the time used to check exercise and exam visibility
     * @return true if the user has a conversation with unread messages in the course, false otherwise
     */
    @Query("""
            SELECT COUNT(conversationParticipant.id) > 0
            FROM ConversationParticipant conversationParticipant
                LEFT JOIN Channel channel ON channel.id = conversationParticipant.conversation.id
                LEFT JOIN channel.exercise exercise
                LEFT JOIN channel.exam exam
            WHERE conversationParticipant.conversation.course.id = :courseId
                AND conversationParticipant.user.id = :userId
                AND conversationParticipant.unreadMessagesCount > 0
                AND conversationParticipant.isMuted = FALSE
                AND (:isTutor = TRUE OR (
                    (exercise.id IS NULL OR (exercise.exerciseGroup IS NULL AND (exercise.releaseDate IS NULL OR exercise.releaseDate < :now)))
                    AND (exercise.id IS NOT NULL OR exam.id IS NULL OR exam.visibleDate < :now)
                ))
            """)
    boolean userHasConversationWithUnreadCountInCourse(@Param("courseId") Long courseId, @Param("userId") Long userId, @Param("isTutor") boolean isTutor,
            @Param("now") ZonedDateTime now);

    /**
     * Remove a deleted top-level post from recipients' unread counts only when it was newer than their read position.
     * The post author never counted their own post, even when a moderator performs the deletion. Unread replies remain counted.
     *
     * @param conversationId conversationId id of the conversation with participants
     * @param authorId       id of the post author, rather than the user deleting the post
     * @param creationDate   creation date of the deleted post
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount - 1
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id <> :authorId
                AND (conversationParticipant.lastRead IS NULL OR conversationParticipant.lastRead < :creationDate)
                AND conversationParticipant.unreadMessagesCount > 0
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
            """)
    void decrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("authorId") Long authorId,
            @Param("creationDate") ZonedDateTime creationDate);
}
