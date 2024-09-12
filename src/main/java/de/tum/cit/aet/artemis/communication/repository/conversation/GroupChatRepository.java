package de.tum.cit.aet.artemis.communication.repository.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface GroupChatRepository extends ArtemisJpaRepository<GroupChat, Long> {

    /**
     * Find all group chats of a given user in a given course.
     * <p>
     * We join the conversionParticipants twice, once to filter the chats and bind it to the user ID. The second time, we fetch all participants.
     *
     * @param courseId the ID of the course to search in
     * @param userId   the ID of the user to search for
     * @return a list of group chats
     */
    @Query("""
            SELECT DISTINCT groupChat
            FROM GroupChat groupChat
                LEFT JOIN groupChat.conversationParticipants conversationParticipant
                LEFT JOIN FETCH groupChat.conversationParticipants conversationParticipants
                LEFT JOIN FETCH conversationParticipants.user user
                LEFT JOIN FETCH user.groups
            WHERE groupChat.course.id = :courseId
                AND conversationParticipant.user.id = :userId
            ORDER BY groupChat.lastMessageDate DESC
            """)
    List<GroupChat> findGroupChatsOfUserWithParticipantsAndUserGroups(@Param("courseId") Long courseId, @Param("userId") Long userId);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);
}
