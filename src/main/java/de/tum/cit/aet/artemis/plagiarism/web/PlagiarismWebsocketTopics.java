package de.tum.cit.aet.artemis.plagiarism.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;

/**
 * The websocket topics of the plagiarism module. The discussion of a plagiarism case is a communication topic, see
 * {@code CommunicationWebsocketTopics#PLAGIARISM_CASE_POSTS}.
 */
@Conditional(PlagiarismEnabled.class)
@Lazy
@Component
public class PlagiarismWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Progress of a plagiarism check of a programming exercise, for the editors who run it.
     */
    public static final WebsocketTopic PROGRAMMING_PLAGIARISM_CHECK = WebsocketTopic.of("/topic/programming-exercises/{exerciseId}/plagiarism-check",
            WebsocketTopicAccess.atLeastEditorInExercise("exerciseId"));

    /**
     * Progress of a plagiarism check of a text exercise, for the editors who run it.
     */
    public static final WebsocketTopic TEXT_PLAGIARISM_CHECK = WebsocketTopic.of("/topic/text-exercises/{exerciseId}/plagiarism-check",
            WebsocketTopicAccess.atLeastEditorInExercise("exerciseId"));
}
