package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
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
                 LEFT JOIN channel.conversationParticipants cp
             WHERE channel.course.id = :courseId
                 AND (channel.isCourseWide IS true OR (channel.id = cp.conversation.id AND cp.user.id = :userId))
             ORDER BY channel.name
            """)
    List<Channel> findChannelsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

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
