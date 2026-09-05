package de.tum.cit.aet.artemis.notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.notification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationParameterDTO;

/**
 * Repository for the {@link CourseNotificationParameter} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseNotificationParameterRepository extends ArtemisJpaRepository<CourseNotificationParameter, Long> {

    /**
     * Get the placeholders of one notification. Cached until the notification is deleted.
     * <p>
     * Answers with the key and value alone, so that the store never holds an entity: the entity references the
     * {@code CourseNotification} it belongs to, which reaches the rest of the domain model.
     *
     * @param notificationId to query for
     *
     * @return The placeholders of the notification.
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.notification.dto.CourseNotificationParameterDTO(p.paramKey, p.paramValue)
            FROM CourseNotificationParameter p
            WHERE p.courseNotification.id = :notificationId
            """)
    @Cacheable(cacheNames = "notificationParameters", key = "'notification_params_' + #notificationId", unless = "#result.isEmpty()")
    Set<CourseNotificationParameterDTO> findByCourseNotificationIdEquals(@Param("notificationId") Long notificationId);
}
