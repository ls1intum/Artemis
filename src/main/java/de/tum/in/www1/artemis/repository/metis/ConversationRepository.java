package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Conversation;

/**
 * Spring Data repository for the Conversation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
             SELECT DISTINCT conversation FROM Conversation conversation
             LEFT JOIN FETCH conversation.conversationParticipants conversationParticipant
             WHERE conversation.id = :#{#conversationId}
             AND conversationParticipant.conversation.id = conversation.id
            """)
    Conversation findConversationById(@Param("conversationId") Long conversationId);

    @Query("""
             SELECT DISTINCT conversation FROM Conversation conversation
             LEFT JOIN FETCH conversation.conversationParticipants conversationParticipant
             WHERE conversation.course.id = :#{#courseId}
             AND EXISTS(
                 SELECT conversationParticipant FROM ConversationParticipant conversationParticipant
                 WHERE conversationParticipant.conversation.id = conversation.id
                 AND conversationParticipant.user.id = :#{#userId}
             )
             ORDER BY conversation.lastMessageDate DESC
            """)
    List<Conversation> findConversationsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
