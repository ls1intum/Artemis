package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteById(long conversationId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourseId(long courseId);

    // This is used only for testing purposes
    List<Conversation> findAllByCourseId(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "conversationParticipants.user" })
    Optional<Conversation> findWithConversationParticipantsById(long conversationId);

    default Conversation findWithConversationParticipantsByIdElseThrow(long conversationId) {
        return this.findWithConversationParticipantsById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    @Query("""
             SELECT COUNT(p.id) > 0
             FROM Conversation c
                 JOIN c.posts p
                 LEFT JOIN ConversationParticipant cp ON c.id = cp.conversation.id AND cp.user.id = :userId
                 LEFT JOIN Channel ch ON c.id = ch.id
             WHERE c.course.id = :courseId
             AND (
                 p.creationDate > cp.lastRead OR
                 (ch.isCourseWide IS true AND cp.id IS null)
             )
            """)
    boolean userHasUnreadMessageInCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
