package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GeneralConversationInfo;
import de.tum.in.www1.artemis.domain.metis.conversation.UserConversationInfo;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteById(long conversationId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);

    // This is used only for testing purposes
    List<Conversation> findAllByCourseId(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user" })
    Optional<Conversation> findWithConversationParticipantsById(long conversationId);

    default Conversation findWithConversationParticipantsByIdElseThrow(long conversationId) {
        return this.findWithConversationParticipantsById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    /**
     * Retrieves a list of user-related information for the provided conversations for the given user
     *
     * @param conversationIds a list of conversation ids you want to retrieve information for
     * @param userId          the user id the information is related to
     * @return a list of user-related conversation info for the provided conversations
     */
    @Query("""
             SELECT new de.tum.in.www1.artemis.domain.metis.conversation.UserConversationInfo (
                 conv.id,
                 cp.id,
                 cp.isModerator,
                 cp.isFavorite,
                 cp.isHidden,
                 cp.lastRead,
                 COUNT(p.id)
             )
             FROM Conversation conv
                 LEFT JOIN Channel channel ON conv.id = channel.id
                 LEFT JOIN ConversationParticipant cp ON conv.id = cp.conversation.id AND cp.user.id = :userId
                 LEFT JOIN Post p ON conv.id = p.conversation.id AND (p.creationDate > cp.lastRead OR (channel.isCourseWide IS true AND cp.lastRead IS null))
             WHERE conv.id IN :conversationIds
                 AND (channel.isCourseWide IS true OR (conv.id = cp.conversation.id AND cp.user.id = :userId))
             GROUP BY conv.id, cp.id, cp.isModerator, cp.isFavorite, cp.isHidden, cp.lastRead
            """)
    List<UserConversationInfo> getUserInformationForConversations(@Param("conversationIds") Iterable<Long> conversationIds, @Param("userId") Long userId);

    /**
     * Retrieves a list of general information for the provided conversations
     *
     * @param conversationIds a list of conversation ids you want to retrieve information for
     * @return a list of user-related conversation info for the provided conversations
     */
    @Query("""
             SELECT new de.tum.in.www1.artemis.domain.metis.conversation.GeneralConversationInfo (
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
             AND (
                 p.creationDate > cp.lastRead OR
                 (ch.isCourseWide IS true AND cp.id IS null)
             )
            """)
    boolean userHasUnreadMessageInCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
