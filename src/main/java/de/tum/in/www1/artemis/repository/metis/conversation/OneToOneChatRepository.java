package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface OneToOneChatRepository extends JpaRepository<OneToOneChat, Long> {

    Set<OneToOneChat> findAllByConversationParticipantsContaining(ConversationParticipant conversationParticipant);

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
    List<OneToOneChat> findActiveOneToOneChatsOfUserWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
                 SELECT o FROM OneToOneChat o
                 LEFT JOIN FETCH o.conversationParticipants p1
                 LEFT JOIN FETCH o.conversationParticipants p2
                 WHERE o.course.id = :courseId
                 AND p1.user.id = :userIdA
                 AND p2.user.id = :userIdB
                 AND p1.conversation = o
                 AND p2.conversation = o
            """)
    Optional<OneToOneChat> findBetweenUsersWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
             SELECT DISTINCT oneToOneChat
             FROM OneToOneChat oneToOneChat
             LEFT JOIN FETCH oneToOneChat.conversationParticipants p
             WHERE oneToOneChat.id = :#{#oneToOneChatId}
            """)
    Optional<OneToOneChat> findByIdWithConversationParticipantsAndUserGroups(@Param("oneToOneChatId") Long oneToOneChatId) throws EntityNotFoundException;

    @Query("""
            SELECT chat
            FROM OneToOneChat chat
                LEFT JOIN chat.conversationParticipants participants
                LEFT JOIN participants.user user
            WHERE user = :user
            """)
    Set<OneToOneChat> findAllByParticipatingUser(User user);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);
}
