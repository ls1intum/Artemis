package de.tum.in.www1.artemis.repository.metis.conversation;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;

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

    @Transactional
    @Modifying
    void deleteById(@NotNull Long conversationId);

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "course" })

    @Query("""
            SELECT DISTINCT c
            FROM Conversation c
                LEFT JOIN FETCH c.conversationParticipants conversationParticipants
                LEFT JOIN FETCH conversationParticipants.user user
            WHERE (user.id = :userId)
            """)
    List<Conversation> findAllWhereUserIsParticipant(@Param("userId") Long userId);
}
