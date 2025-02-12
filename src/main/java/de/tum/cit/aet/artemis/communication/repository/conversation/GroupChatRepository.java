package de.tum.cit.aet.artemis.communication.repository.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

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

    /**
     * Find an existing group chat in a given course with the exact set of participants.
     * <p>
     * This query checks if a group chat exists with the specified participants in a course.
     * It ensures that:
     * - The group chat contains all the provided participants.
     * - The group chat does not include any additional participants.
     * <p>
     * The query uses two subqueries:
     * 1. The first subquery counts how many of the given participants are in the group chat.
     * It ensures that all provided participants are part of the group.
     * 2. The second subquery counts all participants in the group chat.
     * It ensures that there are no extra participants outside the provided set.
     *
     * @param courseId         the ID of the course in which to search for the group chat
     * @param participantIds   the IDs of the users to search for in the group chat
     * @param participantCount the total number of participants expected in the group chat
     * @return an optional group chat with the exact set of participants, if one exists
     */
    @Query("""
            SELECT gc
            FROM GroupChat gc
            WHERE gc.course.id = :courseId
              AND (SELECT COUNT(cp)
                   FROM gc.conversationParticipants cp
                   WHERE cp.user.id IN :participantIds
                  ) = :participantCount
              AND (SELECT COUNT(cp2)
                   FROM gc.conversationParticipants cp2
                  ) = :participantCount
            """)
    Optional<GroupChat> findGroupChatWithExactParticipants(@Param("courseId") Long courseId, @Param("participantIds") List<Long> participantIds,
            @Param("participantCount") long participantCount);

    Integer countByCreatorIdAndCourseId(Long creatorId, Long courseId);
}
