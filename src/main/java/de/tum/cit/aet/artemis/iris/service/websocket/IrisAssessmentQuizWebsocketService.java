package de.tum.cit.aet.artemis.iris.service.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

/**
 * Service for sending Iris assessment quiz updates over websockets.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
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
