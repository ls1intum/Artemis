package de.tum.cit.aet.artemis.lecture.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;

/**
 * The websocket topics of the lecture module.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Component
public class LectureWebsocketTopics implements WebsocketTopicProvider {

    /**
     * The processing state (transcription, ingestion) of the units of a lecture, shown on the lecture unit management page.
     */
    public static final WebsocketTopic UNIT_PROCESSING_STATE = WebsocketTopic.of("/topic/lectures/{lectureId}/unit-processing-state",
            WebsocketTopicAccess.atLeastEditorInLecture("lectureId"));
}
