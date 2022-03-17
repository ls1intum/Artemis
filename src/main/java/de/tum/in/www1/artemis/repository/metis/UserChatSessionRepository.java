package de.tum.in.www1.artemis.repository.metis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.UserChatSession;

/**
 * Spring Data repository for the ChatRepository entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UserChatSessionRepository extends JpaRepository<UserChatSession, Long> {

}
