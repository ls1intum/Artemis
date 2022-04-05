package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.UserChatSession;

/**
 * Spring Data repository for the UserChatSessionRepository entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UserChatSessionRepository extends JpaRepository<UserChatSession, Long> {

    @Query("""
            SELECT DISTINCT userChatSession FROM UserChatSession userChatSession
            WHERE userChatSession.chatSession.id = :#{#chatSessionId}
            """)
    List<UserChatSession> findUserChatSessionsByChatSessionId(@Param("chatSessionId") Long chatSessionId);
}
