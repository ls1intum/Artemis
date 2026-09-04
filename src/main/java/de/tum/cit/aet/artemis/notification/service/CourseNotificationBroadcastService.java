package de.tum.cit.aet.artemis.notification.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;

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
     * Returns a future rather than nothing so that the caller can tell when the channel has finished, and whether it
     * failed. Two of the implementations are {@code @Async}, so a caller that treats the return of this method as
     * completion is observing task submission and nothing more.
     *
     * <p>
     * How much the future promises differs by channel, and a caller must not assume the strongest reading. The webapp and
     * e-mail channels complete once they have done their work and fail exceptionally when it went wrong. The push channel
     * completes once the work has been handed to the relay pipeline: that pipeline is asynchronous several levels deep and
     * deliberately swallows relay errors after retrying, so delivery success is not information it has. See the override
     * for why that is not worth changing for the sake of a counter.
     *
     * @param courseNotification The notification data to be sent
     * @param recipients         The list of recipients who should receive the notification
     * @return completes when this channel has finished what it can observe, exceptionally if that failed
     */
    protected abstract CompletableFuture<Void> sendCourseNotification(CourseNotificationDTO courseNotification, List<CourseNotificationRecipientDTO> recipients);
}
