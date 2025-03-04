package de.tum.cit.aet.artemis.course_notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course_notification.domain.CourseNotificationParameter;

/**
 * Repository for the {@link CourseNotificationParameter} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CourseNotificationParameterRepository extends ArtemisJpaRepository<CourseNotificationParameter, Long> {
}
