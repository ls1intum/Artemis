package de.tum.in.www1.artemis.repository.metis;

import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND conversationParticipant.user.id in :#{#userIds}
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationIdAndUserIds(Long conversationId, Set<Long> userIds);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            """)
    Set<ConversationParticipant> findConversationParticipantByConversationId(@Param("conversationId") Long conversationId);

    Optional<ConversationParticipant> findConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :#{#conversationId}
            AND conversationParticipant.user.id = :#{#userId}
            AND conversationParticipant.isAdmin = true
            """)
    Optional<ConversationParticipant> findAdminConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    Integer countByConversationId(Long conversationId);

    @Transactional
    @Modifying
    // ok because of delete
    void deleteAllByConversationId(Long conversationId);

}
