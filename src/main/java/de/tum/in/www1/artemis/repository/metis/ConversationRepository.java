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
            LEFT JOIN conversation.conversationParticipants conversationParticipants
            WHERE conversation.course.id = :#{#courseId}
            AND conversationParticipants.user.id = :#{#userId}
            ORDER BY conversation.lastMessageDate DESC
            """)
    List<Conversation> getConversationsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
