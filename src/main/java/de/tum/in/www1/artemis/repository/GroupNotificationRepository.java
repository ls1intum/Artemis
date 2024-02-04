package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.GroupNotification;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile("core")
@Repository
public interface GroupNotificationRepository extends JpaRepository<GroupNotification, Long> {

    List<GroupNotification> findAllByCourseId(Long courseId);
}
