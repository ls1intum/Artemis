package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.UserConversationSummary;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.course.id = :#{#courseId}
             ORDER BY channel.name
            """)
    List<Channel> findChannelsByCourseId(@Param("courseId") Long courseId);

    @Query("""
             SELECT channel
             FROM Channel channel
             WHERE channel.lecture.id = :lectureId
            """)
    Channel findChannelByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.exam.id = :examId
            """)
    Channel findChannelByExamId(@Param("examId") Long examId);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.exercise.id = :exerciseId
            """)
    Channel findChannelByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             LEFT JOIN channel.conversationParticipants conversationParticipant
             WHERE channel.course.id = :#{#courseId}
             AND (conversationParticipant.user.id = :#{#userId} OR channel.isCourseWide IS true)
             ORDER BY channel.name
            """)
    List<Channel> findChannelsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
             SELECT new de.tum.in.www1.artemis.domain.metis.conversation.UserConversationSummary (
                                                     channel,
                                                     SUM(CASE WHEN p.id IS NOT null THEN 1L ELSE 0L END)
                                                 )
             FROM Channel channel
             LEFT JOIN channel.conversationParticipants conversationParticipant ON channel.id = conversationParticipant.conversation.id AND conversationParticipant.user.id = :#{#userId}
             LEFT JOIN Post p ON channel.id = p.conversation.id AND (p.creationDate > conversationParticipant.lastRead OR (channel.isCourseWide IS true AND conversationParticipant.lastRead IS null))
             WHERE channel.course.id = :#{#courseId}
             GROUP BY channel
             ORDER BY channel.name
            """)
    List<UserConversationSummary> findChannelsOfUser2(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /*
     * @Query("""
     * SELECT new de.tum.in.www1.artemis.domain.metis.conversation.UserConversationSummary (
     * c.id,
     * SUM(CASE WHEN p.id IS NOT null THEN 1L ELSE 0L END)
     * )
     * FROM Channel c
     * LEFT JOIN ConversationParticipant cp ON c.id = cp.conversation.id AND cp.user.id = :userId
     * LEFT JOIN Post p ON c.id = p.conversation.id AND (p.creationDate > cp.lastRead OR (c.isCourseWide IS true AND cp.lastRead IS null))
     * WHERE c.course.id = :courseId
     * AND (
     * cp.user.id = :userId OR
     * (c.isCourseWide IS true AND cp.id IS null)
     * )
     * GROUP BY c.id
     * """)
     * Set<UserConversationSummary> findChannelsOfUserWithUnreadMessageCount(@Param("courseId") Long courseId, @Param("userId") Long userId);
     */

    @Query("""
             SELECT COUNT(p.id)
             FROM Channel c
                 LEFT JOIN ConversationParticipant cp ON c.id = cp.conversation.id AND cp.user.id = :userId
                 JOIN Post p ON c.id = p.conversation.id
             WHERE c.id = :channelId
                 AND (p.creationDate > cp.lastRead OR (c.isCourseWide IS true AND cp.lastRead IS null))
            """)
    Long countUnreadMessagesInChannelForUser(@Param("channelId") Long channelId, @Param("userId") Long userId);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.course.id = :#{#courseId}
             AND channel.name = :#{#name}
             ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndName(@Param("courseId") Long courseId, @Param("name") String name);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.course.id = :#{#courseId}
             AND channel.name = :#{#name}
             AND channel.id <> :#{#channelId}
             ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndNameAndIdNot(@Param("courseId") Long courseId, @Param("name") String name, @Param("channelId") Long channelId);

    default Channel findByIdElseThrow(long channelId) {
        return this.findById(channelId).orElseThrow(() -> new EntityNotFoundException("Channel", channelId));
    }
}
