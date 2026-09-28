package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;

/**
 * The websocket topics of the notification module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class NotificationWebsocketTopics implements WebsocketTopicProvider {

    /**
     * The active and upcoming system notifications, shown to every user.
     */
    public static final WebsocketTopic SYSTEM_NOTIFICATIONS = WebsocketTopic.of("/topic/notification/system-notification", WebsocketTopicAccess.anyAuthenticatedUser());

    /**
     * The user's notifications of one course.
     */
    public static final WebsocketUserTopic COURSE_NOTIFICATIONS = WebsocketUserTopic.of("/topic/notification/{courseId}");

    /**
     * The user's notifications of all courses.
     */
    public static final WebsocketUserTopic ALL_COURSE_NOTIFICATIONS = WebsocketUserTopic.of("/topic/notification/all");
}
