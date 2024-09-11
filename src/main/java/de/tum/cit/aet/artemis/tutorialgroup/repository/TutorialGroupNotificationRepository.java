package de.tum.cit.aet.artemis.tutorialgroup.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.notification.TutorialGroupNotification;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupNotificationRepository extends ArtemisJpaRepository<TutorialGroupNotification, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
