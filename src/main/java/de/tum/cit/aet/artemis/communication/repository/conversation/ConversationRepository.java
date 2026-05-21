package de.tum.cit.aet.artemis.communication.repository.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.dto.GeneralConversationInfo;
import de.tum.cit.aet.artemis.communication.dto.UserConversationInfo;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ConversationRepository extends ArtemisJpaRepository<Conversation, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants" })
    Optional<Conversation> findWithParticipantsById(long conversationId);

    /**
     * Retrieves a list of user-related information for the provided conversations for the given user
     *
     * @param conversationIds a list of conversation ids you want to retrieve information for
     * @param userId          the user id the information is related to
     * @return a list of user-related conversation info for the provided conversations
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.UserConversationInfo (
                conv.id,
                cp.id,
                cp.isModerator,
                cp.isFavorite,
                cp.isHidden,
                cp.isMuted,
                cp.lastRead,
                (CASE WHEN cp.unreadMessagesCount IS NULL THEN COUNT(p.id) ELSE cp.unreadMessagesCount END)
            )
            FROM Conversation conv
                LEFT JOIN Channel channel ON conv.id = channel.id
                LEFT JOIN FETCH ConversationParticipant cp ON conv.id = cp.conversation.id AND cp.user.id = :userId
                LEFT JOIN Post p ON conv.id = p.conversation.id AND p.author.id <> :userId AND (channel.isCourseWide = TRUE AND cp.lastRead IS NULL)
            WHERE conv.id IN :conversationIds
                AND (channel.isCourseWide = TRUE OR (conv.id = cp.conversation.id AND cp.user.id = :userId))
            GROUP BY conv.id, cp.id, cp.isModerator, cp.isFavorite, cp.isHidden, cp.lastRead, cp.unreadMessagesCount
            """)
    List<UserConversationInfo> getUserInformationForConversations(@Param("conversationIds") Iterable<Long> conversationIds, @Param("userId") Long userId);

    /**
     * Retrieves a list of general information for the provided conversations
     *
     * @param conversationIds a list of conversation ids you want to retrieve information for
     * @return a list of user-related conversation info for the provided conversations
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.GeneralConversationInfo (
                conv.id,
                COUNT(cp.user.id)
            )
            FROM Conversation conv
                LEFT JOIN conv.conversationParticipants cp
            WHERE conv.id IN :conversationIds
            GROUP BY conv.id
            """)
    List<GeneralConversationInfo> getGeneralInformationForConversations(@Param("conversationIds") Iterable<Long> conversationIds);

    @Query("""
            SELECT COUNT(p.id) > 0
            FROM Conversation c
                JOIN c.posts p
                LEFT JOIN ConversationParticipant cp ON c.id = cp.conversation.id AND cp.user.id = :userId
                LEFT JOIN Channel ch ON c.id = ch.id
            WHERE c.course.id = :courseId
            AND p.author.id <> :userId
            AND (
                p.creationDate > cp.lastRead OR
                (ch.isCourseWide = TRUE AND cp.id IS NULL)
            )
            """)
    boolean userHasUnreadMessageInCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT c
            FROM Conversation c
            WHERE c.course.id = :courseId
                AND TYPE(c) = Channel
                AND c.isCourseWide = TRUE
                AND c.id NOT IN (
                    SELECT cp.conversation.id
                    FROM ConversationParticipant cp
                    WHERE cp.user.id = :userId
                )
            """)
    List<Channel> findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Async
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Conversation c
            SET c.lastMessageDate = :now
            WHERE c.id = :conversationId
            """)
    void updateLastMessageDateAsync(@Param("conversationId") Long conversationId, @Param("now") ZonedDateTime now);

    long countByCourseId(long courseId);
}
