package de.tum.cit.aet.artemis.repository.metis.conversation;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.notification.ConversationNotification;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface ConversationNotificationRepository extends ArtemisJpaRepository<ConversationNotification, Long> {

}
