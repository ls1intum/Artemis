package de.tum.cit.aet.artemis.atlas.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;

/**
 * The websocket topics of the Atlas module.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Component
public class AtlasWebsocketTopics implements WebsocketTopicProvider {

    /**
     * A summary of each finished automatic competency orchestration run of a course, shown on the course management pages.
     */
    public static final WebsocketTopic ORCHESTRATION_SUMMARY = WebsocketTopic.of("/topic/atlas/orchestrator/{courseId}", WebsocketTopicAccess.atLeastTutorInCourse("courseId"));
}
