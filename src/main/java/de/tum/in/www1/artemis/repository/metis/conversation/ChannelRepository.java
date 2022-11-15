package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

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
             SELECT DISTINCT channel
             FROM Channel channel
             JOIN channel.conversationParticipants conversationParticipant
             WHERE channel.course.id = :#{#courseId}
             AND conversationParticipant.user.id = :#{#userId}
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
    Optional<Channel> findChannelByCourseIdAndName(@Param("courseId") Long courseId, @Param("name") String name);

    @Query("""
             SELECT DISTINCT channel
             FROM Channel channel
             WHERE channel.course.id = :#{#courseId}
             AND channel.name = :#{#name}
             AND channel.id <> :#{#channelId}
             ORDER BY channel.name
            """)
    Optional<Channel> findChannelByCourseIdAndNameAndIdNot(@Param("courseId") Long courseId, @Param("name") String name, @Param("channelId") Long channelId);

}
