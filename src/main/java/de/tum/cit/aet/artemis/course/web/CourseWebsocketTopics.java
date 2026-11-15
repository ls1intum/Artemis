package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;

/**
 * The websocket topics of the course module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class CourseWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Progress of deleting, resetting, archiving or importing a course, shown on the course management pages.
     */
    public static final WebsocketTopic COURSE_OPERATION_PROGRESS = WebsocketTopic.of("/topic/courses/{courseId}/operation-progress",
            WebsocketTopicAccess.atLeastTutorInCourse("courseId"));
}
