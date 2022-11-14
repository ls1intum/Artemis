package de.tum.in.www1.artemis.repository.metis.conversation;

import static de.tum.in.www1.artemis.service.metis.conversation.OneToOneChatService.ONE_TO_ONE_CHAT_ENTITY_NAME;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface OneToOneChatRepository extends JpaRepository<OneToOneChat, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
                 SELECT DISTINCT oneToOneChat
                 FROM OneToOneChat oneToOneChat
                 LEFT JOIN oneToOneChat.conversationParticipants conversationParticipant
                 LEFT JOIN FETCH oneToOneChat.conversationParticipants
                 WHERE oneToOneChat.course.id = :#{#courseId}
                 AND (oneToOneChat.lastMessageDate IS NOT NULL OR oneToOneChat.creator.id = :#{#userId})
                 AND conversationParticipant.user.id = :#{#userId}
                 ORDER BY oneToOneChat.lastMessageDate DESC
            """)
    List<OneToOneChat> findActiveOneToOneChatsOfUserWithParticipantsAndGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
            SELECT DISTINCT  o FROM OneToOneChat o
            LEFT JOIN FETCH o.conversationParticipants
            WHERE
            o.course.id = :#{#courseId}
            AND
            EXISTS (SELECT p FROM ConversationParticipant p WHERE p.user.id = :#{#userIdA} AND p.conversation = o)
            AND
            EXISTS (SELECT p FROM ConversationParticipant p WHERE p.user.id = :#{#userIdB} AND p.conversation = o)
            """)
    Optional<OneToOneChat> findWithSameMembers(@Param("courseId") Long courseId, @Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
             SELECT DISTINCT oneToOneChat
             FROM OneToOneChat oneToOneChat
             LEFT JOIN FETCH oneToOneChat.conversationParticipants p
             WHERE oneToOneChat.id = :#{#oneToOneChatId}
            """)
    Optional<OneToOneChat> findByIdWithConversationParticipantsAndGroups(@Param("oneToOneChatId") Long oneToOneChatId) throws EntityNotFoundException;

    default OneToOneChat findByIdWithConversationParticipantsAndGroupsElseThrow(long oneToOneChatId) {
        return this.findByIdWithConversationParticipantsAndGroups(oneToOneChatId).orElseThrow(() -> new EntityNotFoundException(ONE_TO_ONE_CHAT_ENTITY_NAME, oneToOneChatId));
    }

}
