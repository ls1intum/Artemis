package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.ChatSession;

/**
 * Spring Data repository for the ChatRepository entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    @Query("""
            SELECT DISTINCT chatSession FROM ChatSession chatSession
            LEFT JOIN chatSession.userChatSessions userChatSession
            WHERE chatSession.course.id = :#{#courseId}
            AND EXISTS (
                SELECT userChatSession from UserChatSession userChatSession
                WHERE userChatSession.user.id = :#{#userId})
            """)
    List<ChatSession> getChatSessionsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
