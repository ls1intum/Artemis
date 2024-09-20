package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

@Repository
public interface OneToOneChatTestRepository extends OneToOneChatRepository {

    @Query("""
            SELECT DISTINCT oneToOneChat
            FROM OneToOneChat oneToOneChat
                LEFT JOIN FETCH oneToOneChat.conversationParticipants p
                LEFT JOIN FETCH p.user u
                LEFT JOIN FETCH u.groups
            WHERE oneToOneChat.id = :oneToOneChatId
            """)
    Optional<OneToOneChat> findByIdWithConversationParticipantsAndUserGroups(@Param("oneToOneChatId") Long oneToOneChatId) throws EntityNotFoundException;
}
