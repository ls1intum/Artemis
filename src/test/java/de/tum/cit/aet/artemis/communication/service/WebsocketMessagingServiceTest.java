package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;

import de.tum.cit.aet.artemis.assessment.web.AssessmentWebsocketTopics;
import de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics;

/**
 * Sends go to the destination of the declared topic, and a broker failure never reaches the code that sends.
 */
class WebsocketMessagingServiceTest {

    private SimpMessageSendingOperations messagingTemplate;

    private WebsocketMessagingService websocketMessagingService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessageSendingOperations.class);
        websocketMessagingService = new WebsocketMessagingService(messagingTemplate, Runnable::run);
    }

    @Test
    void testSendsToTheDestinationOfTheTopic() {
        Message<String> message = MessageBuilder.withPayload("payload").build();
        websocketMessagingService.sendMessage(AssessmentWebsocketTopics.EXERCISE_RESULTS.at(42L), message).join();
        verify(messagingTemplate).send("/topic/exercise/42/newResults", message);

        websocketMessagingService.relayMessage(ExerciseWebsocketTopics.EDITOR_SYNCHRONIZATION.at(7L), message);
        verify(messagingTemplate).send("/topic/exercises/7/synchronization", message);

        websocketMessagingService.sendMessageToUser("student1", AssessmentWebsocketTopics.NEW_RESULTS.at(), "result").join();
        verify(messagingTemplate).convertAndSendToUser("student1", "/topic/newResults", "result");
    }

    @Test
    void testBrokerFailuresDoNotReachTheCaller() {
        doThrow(new MessagingException("broker unavailable")).when(messagingTemplate).send(anyString(), any(Message.class));
        doThrow(new MessagingException("broker unavailable")).when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
        Message<String> message = MessageBuilder.withPayload("payload").build();

        // the relay runs in the calling thread and swallows the failure
        websocketMessagingService.relayMessage(ExerciseWebsocketTopics.EDITOR_SYNCHRONIZATION.at(7L), message);
        // the asynchronous sends report it through their future
        assertThat(websocketMessagingService.sendMessage(AssessmentWebsocketTopics.EXERCISE_RESULTS.at(42L), message)).isCompletedExceptionally();
        assertThat(websocketMessagingService.sendMessageToUser("student1", AssessmentWebsocketTopics.NEW_RESULTS.at(), "result")).isCompletedExceptionally();
    }

    @Test
    void testExecutorRejectionIsReportedThroughTheFuture() {
        Executor rejecting = _ -> {
            throw new IllegalStateException("executor shut down");
        };
        var service = new WebsocketMessagingService(messagingTemplate, rejecting);
        assertThat(service.sendMessage(AssessmentWebsocketTopics.EXERCISE_RESULTS.at(42L), (Object) "payload")).isCompletedExceptionally();
        assertThat(service.sendMessage(AssessmentWebsocketTopics.EXERCISE_RESULTS.at(42L), MessageBuilder.withPayload("payload").build())).isCompletedExceptionally();
        assertThat(service.sendMessageToUser("student1", AssessmentWebsocketTopics.NEW_RESULTS.at(), "result")).isCompletedExceptionally();
    }
}
