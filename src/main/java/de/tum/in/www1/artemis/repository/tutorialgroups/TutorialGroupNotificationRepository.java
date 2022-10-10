package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;

@Repository
public interface TutorialGroupNotificationRepository extends JpaRepository<Notification, Long> {

    List<TutorialGroupNotification> findAllByTutorialGroupId(Long tutorialGroupId);

}
