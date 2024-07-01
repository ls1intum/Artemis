package de.tum.in.www1.artemis.repository.tutorialgroups;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupNotificationRepository extends ArtemisJpaRepository<TutorialGroupNotification, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
