package de.tum.in.www1.artemis.repository.metis;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ChannelRepository extends JpaRepository<Conversation, Long> {

    @Query("""
             SELECT DISTINCT conversation
             FROM Conversation conversation
             LEFT JOIN FETCH conversation.conversationParticipants
             WHERE conversation.course.id = :#{#courseId}
             AND conversation.type = 'CHANNEL'
             ORDER BY conversation.name
            """)
    List<Conversation> findChannelsWithConversationParticipantsByCourseId(@Param("courseId") Long courseId);

    @Query("""
             SELECT DISTINCT conversation
             FROM Conversation conversation
             LEFT JOIN FETCH conversation.conversationParticipants
             WHERE conversation.id = :#{#channelId}
             AND conversation.type = 'CHANNEL'
            """)
    Optional<Conversation> findChannelWithConversationParticipantsById(Long channelId);

    default Conversation findChannelWithConversationParticipantsByIdElseThrow(long channelId) {
        return this.findChannelWithConversationParticipantsById(channelId).orElseThrow(() -> new EntityNotFoundException("Channel", channelId));
    }
}
