package de.tum.in.www1.artemis.repository.metis.conversation;

import java.util.Set;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Transactional
    @Modifying
    void deleteById(@NotNull Long conversationId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCreator(User creator);

    default Conversation findByIdElseThrow(long conversationId) {
        return this.findById(conversationId).orElseThrow(() -> new EntityNotFoundException("Conversation", conversationId));
    }

    Set<Conversation> findAllByCreator(User creator);
}
