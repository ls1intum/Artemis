package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.notification.service.notifications.push_notifications.FirebasePushNotificationService;

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
@Lazy
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
     * @param recipients         A list of recipients who should receive the notification
     */
    @Override
    protected CompletableFuture<Void> sendCourseNotification(CourseNotificationDTO courseNotification, List<CourseNotificationRecipientDTO> recipients) {
        var recipientSet = new HashSet<>(recipients);
        applePushNotificationService.sendCourseNotification(courseNotification, recipientSet);
        firebasePushNotificationService.sendCourseNotification(courseNotification, recipientSet);
        // Completes on dispatch, not on delivery, and deliberately so. Both delegates are @Async void, each one hands
        // batches to sendRelayRequest which is @Async again, and that method swallows a RestClientException after
        // retrying rather than reporting it. Threading futures through all of that would still complete successfully
        // after a swallowed relay failure, so the only way to a real push error rate is to change how push delivery
        // handles its errors. That is a behaviour change to a user-facing delivery path in service of a statistic, so
        // the count is left meaning "handed to the push pipeline" and the admin page says as much.
        return CompletableFuture.completedFuture(null);
    }
}
