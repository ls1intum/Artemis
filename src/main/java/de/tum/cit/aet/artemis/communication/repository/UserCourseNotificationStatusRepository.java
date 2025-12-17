package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationCacheService;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for the {@link UserCourseNotificationStatus} entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserCourseNotificationStatusRepository extends ArtemisJpaRepository<UserCourseNotificationStatus, Long> {

    /**
     * Updates the status of multiple user course notifications for a specific user.
     *
     * <p>
     * Important: Does not invalidate cache by itself. Use {@link CourseNotificationCacheService} for that.
     * </p>
     *
     * @param courseNotificationIds the list of course notification IDs to update
     * @param userId                the ID of the user
     * @param status                the new status to set
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE UserCourseNotificationStatus ucns
            SET ucns.status = :status
            WHERE ucns.user.id = :userId
            AND ucns.courseNotification.id IN :courseNotificationIds
            """)
    void updateUserCourseNotificationStatusForUserIdAndCourseNotificationIds(@Param("courseNotificationIds") List<Long> courseNotificationIds, @Param("userId") Long userId,
            @Param("status") UserCourseNotificationStatusType status);

    /**
     * Updates the status of multiple user course notifications for a specific user.
     *
     * <p>
     * Important: Does not invalidate cache by itself. Use {@link CourseNotificationCacheService} for that.
     * </p>
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @param status   the new status to set
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE UserCourseNotificationStatus ucns
            SET ucns.status = :status
            WHERE ucns.user.id = :userId
                AND ucns.courseNotification.id IN (
                    SELECT ucn.id FROM CourseNotification ucn WHERE ucn.course.id = :courseId
                )
            """)
    void updateUserCourseNotificationStatusForUserIdCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId,
            @Param("status") UserCourseNotificationStatusType status);

    /**
     * Counts the number of unseen course notifications for a specific user in a specific course.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @return the count of unseen notifications
     */
    @Query("""
            SELECT COUNT(ucns)
            FROM UserCourseNotificationStatus ucns
            WHERE ucns.user.id = :userId
            AND ucns.courseNotification.course.id = :courseId
            AND ucns.status = 0
            """)
    @Cacheable(cacheNames = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE, key = "'user_course_notification_count_' + #userId + '_' + #courseId")
    Long countUnseenCourseNotificationsForUserInCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Find all course notification status by user id.
     *
     * @param userId id to query for
     * @return list of course notification status for the user
     */
    List<UserCourseNotificationStatus> findAllByUserId(long userId);
}
