package de.tum.cit.aet.artemis.communication.test_repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;

@Repository
@Primary
public interface ConversationTestRepository extends ConversationRepository {

    List<Conversation> findAllByCourseId(long courseId);
}
