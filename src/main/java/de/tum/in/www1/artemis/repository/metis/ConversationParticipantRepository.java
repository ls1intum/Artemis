package de.tum.in.www1.artemis.repository.metis;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

/**
 * Spring Data repository for the ConversationParticipant entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    @Query("""
            SELECT DISTINCT conversationParticipant FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            """)
    List<ConversationParticipant> findConversationParticipantByConversationId(@Param("conversationId") Long conversationId);

    Optional<ConversationParticipant> findConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);
}
