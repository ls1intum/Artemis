package de.tum.in.www1.artemis.repository.metis.conversation;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Repository
public interface OneToOneChatRepository extends JpaRepository<OneToOneChat, Long> {

    Set<OneToOneChat> findAllByConversationParticipantsContaining(ConversationParticipant conversationParticipant);

    @Query("""
            SELECT DISTINCT oneToOneChat
            FROM OneToOneChat oneToOneChat
                LEFT JOIN oneToOneChat.conversationParticipants matchingParticipant
                LEFT JOIN FETCH oneToOneChat.conversationParticipants allParticipants
                LEFT JOIN FETCH allParticipants.user user
                LEFT JOIN FETCH user.groups
            WHERE oneToOneChat.course.id = :courseId
                AND (oneToOneChat.lastMessageDate IS NOT NULL OR oneToOneChat.creator.id = :userId)
                AND allParticipants.user.id = :userId
            ORDER BY oneToOneChat.lastMessageDate DESC
            """)
    List<OneToOneChat> findActiveOneToOneChatsOfUserWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT o
            FROM OneToOneChat o
                LEFT JOIN FETCH o.conversationParticipants p1
                LEFT JOIN FETCH o.conversationParticipants p2
                LEFT JOIN FETCH p1.user u1
                LEFT JOIN FETCH p2.user u2
                LEFT JOIN FETCH u1.groups
                LEFT JOIN FETCH u2.groups
            WHERE o.course.id = :courseId
                AND u1.id = :userIdA
                AND u2.id = :userIdB
            """)
    Optional<OneToOneChat> findBetweenUsersWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    @Query("""
            SELECT DISTINCT oneToOneChat
            FROM OneToOneChat oneToOneChat
                LEFT JOIN FETCH oneToOneChat.conversationParticipants p
                LEFT JOIN FETCH p.user u
                LEFT JOIN FETCH u.groups
            WHERE oneToOneChat.id = :oneToOneChatId
            """)
    Optional<OneToOneChat> findByIdWithConversationParticipantsAndUserGroups(@Param("oneToOneChatId") Long oneToOneChatId) throws EntityNotFoundException;

    @Query("""
            SELECT chat
            FROM OneToOneChat chat
                LEFT JOIN chat.conversationParticipants participants
                LEFT JOIN participants.user user
            WHERE user = :user
            """)
    Set<OneToOneChat> findAllByParticipatingUser(@Param("user") User user);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);
}
