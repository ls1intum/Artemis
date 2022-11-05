package de.tum.in.www1.artemis.repository.metis.conversation;

import static de.tum.in.www1.artemis.service.metis.conversation.GroupChatService.GROUP_CHAT_ENTITY_NAME;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    @Query("""
             SELECT DISTINCT groupChat
             FROM GroupChat groupChat
             LEFT JOIN FETCH groupChat.conversationParticipants
             WHERE groupChat.id = :#{#groupChatId}
            """)
    Optional<GroupChat> findByIdWithConversationParticipants(@Param("groupChatId") Long groupChatId) throws EntityNotFoundException;

    default GroupChat findByIdWithConversationParticipantsElseThrow(long groupChatId) {
        return this.findByIdWithConversationParticipants(groupChatId).orElseThrow(() -> new EntityNotFoundException(GROUP_CHAT_ENTITY_NAME, groupChatId));
    }

}
