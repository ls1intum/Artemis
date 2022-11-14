package de.tum.in.www1.artemis.repository.metis.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

@Repository

public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {
}
