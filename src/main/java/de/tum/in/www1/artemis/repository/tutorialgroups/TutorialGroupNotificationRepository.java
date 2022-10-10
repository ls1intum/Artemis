package de.tum.in.www1.artemis.repository.tutorialgroups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.Notification;

@Repository
public interface TutorialGroupNotificationRepository extends JpaRepository<Notification, Long> {
}
