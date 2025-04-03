package de.tum.cit.aet.artemis.tutorialgroup.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;

@Conditional(TutorialGroupEnabled.class)
@Repository
public interface TutorialGroupNotificationRepository extends ArtemisJpaRepository<TutorialGroupNotification, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
