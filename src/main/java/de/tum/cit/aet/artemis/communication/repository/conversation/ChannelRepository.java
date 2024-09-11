package de.tum.cit.aet.artemis.communication.repository.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;

@Profile(PROFILE_CORE)
@Repository
public interface ChannelRepository extends ArtemisJpaRepository<Channel, Long> {

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
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
                AND (
                   channel.isCourseWide = TRUE
                   OR (channel.id = cp.conversation.id AND cp.user.id = :userId))
            ORDER BY channel.name
            """)
    List<Channel> findChannelsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
                AND channel.isCourseWide = TRUE
            ORDER BY channel.name
            """)
    List<Channel> findCourseWideChannelsInCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
                AND channel.name = :name
            ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndName(@Param("courseId") Long courseId, @Param("name") String name);

    boolean existsChannelByNameAndCourseId(String name, Long courseId);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
                AND channel.name = :name
                AND channel.id <> :channelId
            ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndNameAndIdNot(@Param("courseId") Long courseId, @Param("name") String name, @Param("channelId") Long channelId);
}
