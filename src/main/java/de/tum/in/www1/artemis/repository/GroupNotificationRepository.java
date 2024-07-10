package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface GroupNotificationRepository extends ArtemisJpaRepository<GroupNotification, Long> {

    List<GroupNotification> findAllByCourseId(Long courseId);
}
