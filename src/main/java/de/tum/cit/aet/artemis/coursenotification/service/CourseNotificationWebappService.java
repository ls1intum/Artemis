package de.tum.cit.aet.artemis.coursenotification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.coursenotification.dto.CourseNotificationDTO;

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
@Service
public class CourseNotificationWebappService extends CourseNotificationBroadcastService {

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/coursenotification/";

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
     * @param recipients         The list of users who should receive the notification in the web app
     */
    @Async
    @Override
    protected void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients) {
        recipients.forEach(user -> {
            websocketMessagingService.sendMessageToUser(user.getLogin(), WEBSOCKET_TOPIC_PREFIX + courseNotification.courseId(), courseNotification);
        });
    }
}
