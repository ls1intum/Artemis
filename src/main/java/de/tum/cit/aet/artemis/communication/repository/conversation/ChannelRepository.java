package de.tum.cit.aet.artemis.communication.repository.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.ChannelSubTypeReferenceDatesDTO;
import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ChannelRepository extends ArtemisJpaRepository<Channel, Long> {

    // Indexable channels match ChannelSearchableEntityDTO.isIndexable: not archived AND (course-wide OR public).
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO(channel.course.id, channel.id)
            FROM Channel channel
            WHERE channel.course.id IN :courseIds
                AND channel.isArchived = FALSE
                AND (channel.isCourseWide = TRUE OR channel.isPublic = TRUE)
            """)
    List<CourseEntityIdDTO> findIndexableChannelIdCourseIdPairsForCourses(@Param("courseIds") Collection<Long> courseIds);

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
            WHERE channel.course.id = :courseId AND channel.lecture IS NOT NULL
            """)
    Set<Channel> findLectureChannelsByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT channel.name
            FROM Channel channel
            WHERE channel.lecture.id = :lectureId
            """)
    String findChannelNameByLectureId(@Param("lectureId") Long lectureId);

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
            SELECT DISTINCT channel.id
            FROM Channel channel
            WHERE channel.exercise.id = :exerciseId
            """)
    Long findChannelIdByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
                LEFT JOIN channel.conversationParticipants conversationParticipant
                LEFT JOIN channel.lecture lecture
                LEFT JOIN FETCH channel.course
                LEFT JOIN FETCH channel.creator
            WHERE channel.course.id = :courseId
                AND (channel.isCourseWide OR (channel.id = conversationParticipant.conversation.id AND conversationParticipant.user.id = :userId))
                AND (lecture IS NULL OR NOT lecture.isTutorialLecture)
            ORDER BY channel.name
            """)
    List<Channel> findChannelsOfUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
                AND channel.name = :name
            ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndName(@Param("courseId") Long courseId, @Param("name") String name);

    boolean existsChannelByNameAndCourseId(String name, Long courseId);

    boolean existsByCourseIdAndNameIn(Long courseId, Collection<String> names);

    @Query("""
            SELECT DISTINCT channel
            FROM Channel channel
            WHERE channel.course.id = :courseId
                AND channel.name = :name
                AND channel.id <> :channelId
            ORDER BY channel.name
            """)
    Set<Channel> findChannelByCourseIdAndNameAndIdNot(@Param("courseId") Long courseId, @Param("name") String name, @Param("channelId") Long channelId);

    /**
     * Projects the dates of the exercise, lecture or exam the given channels belong to.
     * <p>
     * The conversation sidebar marks a channel as current when its referenced item is happening around now. Taking the
     * dates off {@code channel.getExercise()} and friends would resolve one lazy proxy per channel; this answers for all
     * of them in a single query and reads three columns rather than three entity graphs.
     *
     * @param channelIds the channels to look up
     * @return the reference dates, one entry per channel that references an exercise, lecture or exam
     */
    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.communication.dto.ChannelSubTypeReferenceDatesDTO(
                channel.id,
                COALESCE(exercise.releaseDate, lecture.startDate, exam.startDate),
                COALESCE(exercise.dueDate, lecture.endDate, exam.endDate))
            FROM Channel channel
                LEFT JOIN channel.exercise exercise
                LEFT JOIN channel.lecture lecture
                LEFT JOIN channel.exam exam
            WHERE channel.id IN :channelIds
                AND (channel.exercise IS NOT NULL OR channel.lecture IS NOT NULL OR channel.exam IS NOT NULL)
            """)
    Set<ChannelSubTypeReferenceDatesDTO> findSubTypeReferenceDates(@Param("channelIds") Collection<Long> channelIds);
}
