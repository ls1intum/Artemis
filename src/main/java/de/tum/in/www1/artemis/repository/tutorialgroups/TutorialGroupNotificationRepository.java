package de.tum.in.www1.artemis.repository.tutorialgroups;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.transaction.Transactional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupNotificationRepository extends JpaRepository<TutorialGroupNotification, Long> {

    @Transactional
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
