package de.tum.cit.aet.artemis.iris.service.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

class IrisAssessmentQuizWebsocketServiceTest {

    private WebsocketMessagingService websocketMessagingService;

    private IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService;

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        irisAssessmentQuizWebsocketService = new IrisAssessmentQuizWebsocketService(websocketMessagingService);
    }

    @Test
    void sendInClassQuizStartedSendsEmptyPayloadToExerciseSpecificTopic() {
        irisAssessmentQuizWebsocketService.sendInClassQuizStarted(42L);

        verify(websocketMessagingService).sendMessage("/topic/iris/programming-exercises/42/assessment-quiz/in-class/start", "");
    }

    @Test
    void getInClassQuizStartedTopicBuildsTopicForExerciseId() {
        var topic = IrisAssessmentQuizWebsocketService.getInClassQuizStartedTopic(7L);

        assertThat(topic).isEqualTo("/topic/iris/programming-exercises/7/assessment-quiz/in-class/start");
    }
}
