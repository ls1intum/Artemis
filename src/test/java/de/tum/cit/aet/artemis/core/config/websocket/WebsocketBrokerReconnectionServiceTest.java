package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class WebsocketBrokerReconnectionServiceTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private StompBrokerRelayMessageHandler stompBrokerRelayMessageHandler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    @BeforeEach
    void setUp() {
        websocketBrokerReconnectionService = new WebsocketBrokerReconnectionService(taskScheduler, Optional.of(stompBrokerRelayMessageHandler));
    }

    @Test
    void shouldStartReconnectAttemptsOnBrokerLoss() {
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(stompBrokerRelayMessageHandler.isRunning()).thenReturn(true);
        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));

        verify(taskScheduler).scheduleWithFixedDelay(runnableCaptor.capture(), any(Instant.class), eq(WebsocketBrokerReconnectionService.RECONNECT_INTERVAL));
        verify(stompBrokerRelayMessageHandler).stop();
        verify(stompBrokerRelayMessageHandler).start();

        runnableCaptor.getValue().run();

        verify(stompBrokerRelayMessageHandler, times(2)).stop();
        verify(stompBrokerRelayMessageHandler, times(2)).start();
    }

    @Test
    void shouldStopReconnectAttemptsWhenBrokerAvailable() {
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(stompBrokerRelayMessageHandler.isRunning()).thenReturn(true);
        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));
        verify(taskScheduler).scheduleWithFixedDelay(runnableCaptor.capture(), any(Instant.class), eq(WebsocketBrokerReconnectionService.RECONNECT_INTERVAL));

        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(true, new Object()));
        verify(scheduledFuture).cancel(false);

        runnableCaptor.getValue().run();
        verify(stompBrokerRelayMessageHandler, times(1)).start();
    }

    @Test
    void manualReconnectSkippedWithoutRelay() {
        var serviceWithoutRelay = new WebsocketBrokerReconnectionService(taskScheduler, Optional.empty());
        assertThat(serviceWithoutRelay.triggerManualReconnect()).isFalse();
        verifyNoInteractions(taskScheduler);
    }
}
