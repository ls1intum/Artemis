package de.tum.in.www1.artemis.repository.metis.conversation;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    @Query("""
            SELECT DISTINCT groupChat
            FROM GroupChat groupChat
                LEFT JOIN FETCH groupChat.conversationParticipants conversationParticipant
                LEFT JOIN FETCH conversationParticipant.user user
                LEFT JOIN FETCH user.groups
            WHERE groupChat.course.id = :courseId
                AND user.id = :userId
            ORDER BY groupChat.lastMessageDate DESC
            """)
    List<GroupChat> findGroupChatsOfUserWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);

    default GroupChat findByIdElseThrow(long groupChatId) {
        return this.findById(groupChatId).orElseThrow(() -> new EntityNotFoundException("GroupChat", groupChatId));
    }

}
