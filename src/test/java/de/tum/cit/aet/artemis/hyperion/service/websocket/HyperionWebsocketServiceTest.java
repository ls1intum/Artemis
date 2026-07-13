package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

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
    void send_swallowsExecutionException_soADeliveryFailureNeverAbortsTheRun() {
        CompletableFuture<Void> failed = CompletableFuture.failedFuture(new ExecutionException("broker down", new IllegalStateException()));
        when(messagingService.sendMessageToUser(eq("instructor1"), anyString(), any())).thenReturn(failed);

        // A broker/delivery failure must not propagate out of send(): the caller (the generation loop) keeps running and still emits its terminal event.
        assertThatCode(() -> service.send("instructor1", "jobs/x", "payload")).doesNotThrowAnyException();
    }

    @Test
    void send_swallowsInterruptionButRestoresTheInterruptFlag() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> interrupting = mock(CompletableFuture.class);
        when(interrupting.get()).thenThrow(new InterruptedException("interrupted"));
        when(messagingService.sendMessageToUser(eq("instructor1"), anyString(), any())).thenReturn(interrupting);

        // An InterruptedException raised while awaiting delivery must be caught inside send() (not propagated), but the interrupt flag must be RESTORED so the caller's loop can
        // still observe the interruption rather than losing it. Thread.interrupted() both asserts and clears the flag so it does not leak into the next test.
        assertThatCode(() -> service.send("instructor1", "jobs/x", "payload")).doesNotThrowAnyException();
        assertThat(Thread.interrupted()).isTrue();
    }
}
