package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
            SELECT DISTINCT conversation
            FROM Conversation conversation
            LEFT JOIN FETCH conversation.conversationParticipants p
            WHERE conversation.id = :#{#conversationId}
            """)
    Optional<Conversation> findByIdWithConversationParticipantsAndGroups(@Param("conversationId") Long conversationId);

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    default Conversation findByIdWithConversationParticipantsAndGroupsElseThrow(long conversationId) {
        return this.findByIdWithConversationParticipantsAndGroups(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

}
