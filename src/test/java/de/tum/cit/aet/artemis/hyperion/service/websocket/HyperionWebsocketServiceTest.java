package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationCancellationEvent;

class HyperionWebsocketServiceTest {

    private WebsocketMessagingService messagingService;

    private HyperionWebsocketService service;

    @BeforeEach
    void setUp() {
        messagingService = mock(WebsocketMessagingService.class);
        service = new HyperionWebsocketService(messagingService);
    }

    @Test
    void send_prefixesTheHyperionTopicAndForwardsPayloadToTheUser() {
        when(messagingService.sendMessageToUser(eq("instructor1"), eq("/topic/hyperion/exercise-generation/jobs/job-42"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        Object payload = new Object();

        service.send("instructor1", "exercise-generation/jobs/job-42", payload);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingService).sendMessageToUser(eq("instructor1"), eq("/topic/hyperion/exercise-generation/jobs/job-42"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
    }

    @Test
    void sendCancellation_forwardsTheRetainedTerminalEventToTheJobTopic() {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, "cancelled");
        when(messagingService.sendMessageToUser("instructor1", "/topic/hyperion/exercise-generation/jobs/job-42", event)).thenReturn(CompletableFuture.completedFuture(null));

        service.sendCancellation(new GenerationCancellationEvent("instructor1", "job-42", event));

        verify(messagingService).sendMessageToUser("instructor1", "/topic/hyperion/exercise-generation/jobs/job-42", event);
    }

    @Test
    void broadcastExerciseState_usesThePublicExerciseTopic() {
        ExerciseGenerationStateDTO state = new ExerciseGenerationStateDTO(42L, "job-42", true);
        when(messagingService.sendMessage("/topic/hyperion/exercise-generation/exercises/42/state", state)).thenReturn(CompletableFuture.completedFuture(null));

        service.broadcastExerciseState(new ExerciseGenerationStateChangedEvent(state));

        verify(messagingService).sendMessage("/topic/hyperion/exercise-generation/exercises/42/state", state);
    }

    @Test
    void send_swallowsExecutionException_soADeliveryFailureNeverAbortsTheRun() {
        CompletableFuture<Void> failed = CompletableFuture.failedFuture(new ExecutionException("broker down", new IllegalStateException()));
        when(messagingService.sendMessageToUser(eq("instructor1"), anyString(), any())).thenReturn(failed);

        // A broker/delivery failure must not propagate out of send(): the caller (the generation loop) keeps running and still emits its terminal event.
        assertThatCode(() -> service.send("instructor1", "jobs/x", "payload")).doesNotThrowAnyException();
    }

    @Test
    void send_doesNotWaitForBrokerDelivery() {
        CompletableFuture<Void> brokerDelivery = new CompletableFuture<>();
        when(messagingService.sendMessageToUser(eq("instructor1"), anyString(), any())).thenReturn(brokerDelivery);

        CompletableFuture<Void> invocation = CompletableFuture.runAsync(() -> service.send("instructor1", "jobs/x", "payload"));
        try {
            assertThat(invocation).succeedsWithin(Duration.ofSeconds(1));
        }
        finally {
            brokerDelivery.complete(null);
        }
    }
}
