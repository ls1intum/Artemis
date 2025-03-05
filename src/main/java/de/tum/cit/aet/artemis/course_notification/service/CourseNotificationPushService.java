package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;

/**
 * Service responsible for sending course notifications via push notifications to mobile devices.
 *
 * <p>
 * This implementation of {@link CourseNotificationBroadcastService} handles delivery of notifications
 * to mobile devices using platform-specific push notification services. It supports both
 * iOS devices (via APNS) and Android devices (via Firebase).
 * Notifications are sent asynchronously to both platforms in parallel.
 * </p>
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationPushService extends CourseNotificationBroadcastService {

    private final ApplePushNotificationService applePushNotificationService;

    private final FirebasePushNotificationService firebasePushNotificationService;

    public CourseNotificationPushService(ApplePushNotificationService applePushNotificationService, FirebasePushNotificationService firebasePushNotificationService) {
        this.applePushNotificationService = applePushNotificationService;
        this.firebasePushNotificationService = firebasePushNotificationService;
    }

    /**
     * Sends a course notification to a list of recipients using multiple notification services.
     * This method forwards the notification to both Apple and Firebase push notification services
     * to ensure delivery across different device platforms. These methods are ran asynchronously.
     *
     * @param courseNotification The DTO containing the course notification details to be sent
     * @param recipients         A list of User objects who should receive the notification
     */
    @Override
    protected void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients) {
        var recipientSet = new HashSet<>(recipients);
        applePushNotificationService.sendCourseNotification(courseNotification, recipientSet);
        firebasePushNotificationService.sendCourseNotification(courseNotification, recipientSet);
    }
}
