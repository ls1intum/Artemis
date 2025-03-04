package de.tum.cit.aet.artemis.course_notification.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course_notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationStatusType;

/**
 * Repository for the {@link CourseNotification} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CourseNotificationRepository extends ArtemisJpaRepository<CourseNotification, Long> {

    /**
     * Find all course notifications for a specific user and course where the status is not archived (value 2)
     * according to {{@link UserCourseNotificationStatusType}}. This query includes the eagerly fetched parameters,
     * since we always need those to instantiate the notification.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @return list of course notifications that match the criteria
     */
    @Query("""
            SELECT DISTINCT cn FROM CourseNotification cn
            LEFT JOIN FETCH cn.parameters
            JOIN cn.userStatuses us
            WHERE us.user.id = :userId
            AND cn.course.id = :courseId
            AND us.status <> 2
            """)
    Page<CourseNotification> findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(@Param("userId") Long userId, @Param("courseId") Long courseId, Pageable pageable);
}
