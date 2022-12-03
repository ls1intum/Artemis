package de.tum.in.www1.artemis.repository.tutorialgroups;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;

@Repository
public interface TutorialGroupNotificationRepository extends JpaRepository<TutorialGroupNotification, Long> {

    @Transactional
    @Modifying
    void deleteAllByTutorialGroupId(Long tutorialGroupId);
}
