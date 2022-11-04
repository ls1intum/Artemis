package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
             SELECT DISTINCT conversation
             FROM Conversation conversation
             LEFT JOIN FETCH conversation.conversationParticipants
             WHERE conversation.id = :#{#conversationId}
            """)
    Conversation findConversationByIdWithConversationParticipants(@Param("conversationId") Long conversationId);

    // we have to JOIN twice because JPA does not allow to define an ALIAS ('conversationParticipant' in our case) after JOIN FETCH
    // see: https://stackoverflow.com/questions/5816417/how-to-properly-express-jpql-join-fetch-with-where-clause-as-jpa-2-criteriaq
    @Query("""
             SELECT DISTINCT conversation
             FROM Conversation conversation
             LEFT JOIN conversation.conversationParticipants conversationParticipant
             LEFT JOIN FETCH conversation.conversationParticipants
             WHERE conversation.course.id = :#{#courseId}
             AND conversationParticipant.user.id = :#{#userId}
             ORDER BY conversation.lastMessageDate DESC
            """)
    List<Conversation> findConversationsOfUserWithConversationParticipants(@Param("courseId") Long courseId, @Param("userId") Long userId);

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

}
