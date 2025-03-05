package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.course_notification.repository.UserCourseNotificationStatusRepository;

/**
 * Service for managing user course notification statuses.
 */
@Profile(PROFILE_CORE)
@Service
public class UserCourseNotificationStatusService {

    private final UserCourseNotificationStatusRepository userCourseNotificationStatusRepository;

    private final CourseNotificationCacheService courseNotificationCacheService;

    public UserCourseNotificationStatusService(UserCourseNotificationStatusRepository userCourseNotificationStatusRepository,
            CourseNotificationCacheService courseNotificationCacheService) {
        this.userCourseNotificationStatusRepository = userCourseNotificationStatusRepository;
        this.courseNotificationCacheService = courseNotificationCacheService;
    }

    /**
     * Creates notification status entries for multiple users for a specific course notification.
     * This method creates a {@link UserCourseNotificationStatus} entry with UNSEEN status for each user
     * in the provided set and invalidates their notification caches.
     *
     * @param users                Set of users to create notification status entries for
     * @param courseNotificationId The ID of the course notification
     * @param courseId             The ID of the course, needed for cache invalidation
     */
    protected void batchCreateStatusForUsers(Set<User> users, long courseNotificationId, long courseId) {
        var courseNotification = new CourseNotification();
        courseNotification.setId(courseNotificationId);

        var status = new ArrayList<UserCourseNotificationStatus>();

        for (var user : users) {
            status.add(new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN));
        }

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(users, courseId);

        userCourseNotificationStatusRepository.saveAll(status);
    }

    /**
     * Updates the status of multiple course notifications for a specific user.
     * This method changes the status of all specified course notifications to the provided
     * new status for the given user and invalidates their notification cache.
     *
     * @param user                  The user whose notification statuses will be updated
     * @param courseNotificationIds List of course notification IDs to update
     * @param newStatus             The new status to set for the notifications
     * @param courseId              The id of the course for cache invalidation
     */
    public void updateUserCourseNotificationStatus(User user, List<Long> courseNotificationIds, UserCourseNotificationStatusType newStatus, long courseId) {
        userCourseNotificationStatusRepository.updateUserCourseNotificationStatusForUserIdAndCourseNotificationIds(courseNotificationIds, user.getId(), newStatus);

        courseNotificationCacheService.invalidateCourseNotificationCacheForUsers(Set.of(user), courseId);
    }
}
