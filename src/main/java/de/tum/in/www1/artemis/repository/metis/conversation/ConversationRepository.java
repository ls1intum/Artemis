package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.Optional;

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
    Optional<Conversation> findByIdWithConversationParticipants(@Param("conversationId") Long conversationId);

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    default Conversation findByIdWithConversationParticipantsElseThrow(long conversationId) {
        return this.findByIdWithConversationParticipants(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

}
