package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    @Query("""
             SELECT DISTINCT groupChat
             FROM GroupChat groupChat
             LEFT JOIN groupChat.conversationParticipants conversationParticipant
             LEFT JOIN FETCH groupChat.conversationParticipants
             WHERE groupChat.course.id = :#{#courseId}
             AND groupChat.lastMessageDate IS NOT NULL
             AND conversationParticipant.user.id = :#{#userId}
             ORDER BY groupChat.lastMessageDate DESC
            """)
    List<GroupChat> findActiveGroupChatsOfUserWithConversationParticipants(@Param("courseId") Long courseId, @Param("userId") Long userId);

    // we have to JOIN twice because JPA does not allow to define an ALIAS ('conversationParticipant' in our case) after JOIN FETCH
    // see: https://stackoverflow.com/questions/5816417/how-to-properly-express-jpql-join-fetch-with-where-clause-as-jpa-2-criteriaq
    @Query("""
             SELECT DISTINCT groupChat
             FROM GroupChat groupChat
             LEFT JOIN groupChat.conversationParticipants conversationParticipant
             LEFT JOIN FETCH groupChat.conversationParticipants
             WHERE groupChat.course.id = :#{#courseId}
             AND conversationParticipant.user.id = :#{#userId}
             ORDER BY groupChat.lastMessageDate DESC
            """)
    List<GroupChat> findConversationsOfUserWithConversationParticipants(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
