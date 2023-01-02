package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user.groups" })
    @Query("""
                 SELECT DISTINCT groupChat
                 FROM GroupChat groupChat
                 LEFT JOIN groupChat.conversationParticipants conversationParticipant
                 LEFT JOIN FETCH groupChat.conversationParticipants
                 WHERE groupChat.course.id = :#{#courseId}
                 AND conversationParticipant.user.id = :#{#userId}
                 ORDER BY groupChat.lastMessageDate DESC
            """)
    List<GroupChat> findGroupChatsOfUserWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);

    default GroupChat findByIdElseThrow(long groupChatId) {
        return this.findById(groupChatId).orElseThrow(() -> new EntityNotFoundException("GroupChat", groupChatId));
    }

}
