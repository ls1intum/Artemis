package de.tum.cit.aet.artemis.iris.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * The websocket topics of the Iris module. All of them are per-user topics: each message belongs to the user who owns the chat session or started the job.
 */
@Conditional(IrisEnabled.class)
@Lazy
@Component
public class IrisWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Messages, status updates and suggestions of one chat session.
     */
    public static final WebsocketUserTopic SESSION = WebsocketUserTopic.of("/topic/iris/{sessionId}");

    /**
     * Commands Iris asks the client of one chat session to apply.
     */
    public static final WebsocketUserTopic SESSION_COMMANDS = WebsocketUserTopic.of("/topic/iris/{sessionId}/commands");

    /**
     * Struggle interventions for the student.
     */
    public static final WebsocketUserTopic STRUGGLE_INTERVENTION = WebsocketUserTopic.of("/topic/iris/struggle-intervention");

    /**
     * Progress of a competency generation job in a course.
     */
    public static final WebsocketUserTopic COMPETENCY_GENERATION = WebsocketUserTopic.of("/topic/iris/competencies/{courseId}");

    /**
     * The answer to a global search question.
     */
    public static final WebsocketUserTopic GLOBAL_SEARCH_ANSWER = WebsocketUserTopic.of("/topic/iris/global-search-answer");
}
