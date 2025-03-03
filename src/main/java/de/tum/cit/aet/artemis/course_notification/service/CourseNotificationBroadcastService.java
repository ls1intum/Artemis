package de.tum.cit.aet.artemis.course_notification.service;

import java.util.List;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;

/**
 * Interface for services that broadcast course notifications to users.
 *
 * <p>
 * This interface defines the contract for notification services that can
 * send course-related notifications to a list of recipients. Implementations
 * might include different delivery methods such as email, push notifications,
 * or in-app notifications.
 * </p>
 */
public interface CourseNotificationBroadcastService {

    /**
     * Sends a course notification to a list of recipients.
     *
     * <p>
     * This method handles the delivery of a course notification to multiple users.
     * Implementations should handle any delivery-specific logic, such as formatting
     * the notification content appropriately for the delivery channel.
     * </p>
     *
     * @param courseNotification The notification data to be sent
     * @param recipients         The list of users who should receive the notification
     */
    void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients);
}
