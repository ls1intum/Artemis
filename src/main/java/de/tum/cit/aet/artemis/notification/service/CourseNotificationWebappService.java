package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;

/**
 * Service responsible for delivering course notifications to the web application via websockets.
 *
 * <p>
 * This implementation of {@link CourseNotificationBroadcastService} handles the delivery of notifications
 * to users through course-specific websocket topics. Each notification is sent to a topic
 * dedicated to a specific course, allowing users to receive real-time updates for courses they are
 * subscribed to in the web application.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseNotificationWebappService extends CourseNotificationBroadcastService {

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/notification/";

    private static final String WEBSOCKET_BROADCAST_TOPIC_PREFIX = "/topic/notification/all";

    private final WebsocketMessagingService websocketMessagingService;

    public CourseNotificationWebappService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Asynchronously sends course notifications to users via websocket connections.
     *
     * <p>
     * This method iterates through each recipient and sends the notification to a
     * course-specific websocket topic that the user is subscribed to. This enables
     * real-time delivery of notifications within the web application interface.
     * </p>
     *
     * @param courseNotification The notification data to be sent
     * @param recipients         The list of recipients who should receive the notification in the web app
     */
    @Async
    @Override
    protected CompletableFuture<Void> sendCourseNotification(CourseNotificationDTO courseNotification, List<CourseNotificationRecipientDTO> recipients) {
        // Every send returns its own future and all of them are composed, so this method reports the outcome of the
        // websocket work rather than of having started it. Discarding them completed this future immediately, and a
        // broker failure afterwards was recorded as a successful delivery with dispatch-only latency.
        var sends = new ArrayList<CompletableFuture<Void>>();
        recipients.forEach(recipient -> {
            sends.add(websocketMessagingService.sendMessageToUser(recipient.login(), WEBSOCKET_TOPIC_PREFIX + courseNotification.courseId(), courseNotification));
            sends.add(websocketMessagingService.sendMessageToUser(recipient.login(), WEBSOCKET_BROADCAST_TOPIC_PREFIX, courseNotification));
        });
        return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new));
    }
}
