package de.tum.cit.aet.artemis.iris.service.websocket;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Service for sending Iris assessment quiz updates over websockets.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisAssessmentQuizWebsocketService {

    private static final String IN_CLASS_QUIZ_STARTED_TOPIC = "/topic/iris/programming-exercises/%d/assessment-quiz/in-class/start";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisAssessmentQuizWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public void sendInClassQuizStarted(long exerciseId) {
        websocketMessagingService.sendMessage(getInClassQuizStartedTopic(exerciseId), "");
    }

    public static String getInClassQuizStartedTopic(long exerciseId) {
        return IN_CLASS_QUIZ_STARTED_TOPIC.formatted(exerciseId);
    }
}
