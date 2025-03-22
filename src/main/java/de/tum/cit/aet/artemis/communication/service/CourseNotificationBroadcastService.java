package de.tum.cit.aet.artemis.communication.service;

import java.util.List;

import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.core.domain.User;

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
public abstract class CourseNotificationBroadcastService {

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
    protected abstract void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients);
}
