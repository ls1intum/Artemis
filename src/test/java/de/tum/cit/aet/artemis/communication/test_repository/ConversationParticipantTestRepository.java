package de.tum.cit.aet.artemis.communication.test_repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;

@Repository
public interface ConversationParticipantTestRepository extends ConversationParticipantRepository {

    default ConversationParticipant findConversationParticipantByConversationIdAndUserIdElseThrow(Long conversationId, Long userId) {
        return getValueElseThrow(findConversationParticipantByConversationIdAndUserId(conversationId, userId));
    }
}
