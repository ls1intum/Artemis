package de.tum.cit.aet.artemis.coursenotification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.coursenotification.domain.CourseNotificationParameter;

/**
 * Repository for the {@link CourseNotificationParameter} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CourseNotificationParameterRepository extends ArtemisJpaRepository<CourseNotificationParameter, Long> {

    @Cacheable(cacheNames = "notificationParameters", key = "'notification_params_' + #notificationId", unless = "#result.isEmpty()")
    Set<CourseNotificationParameter> findByCourseNotificationIdEquals(Long notificationId);
}
