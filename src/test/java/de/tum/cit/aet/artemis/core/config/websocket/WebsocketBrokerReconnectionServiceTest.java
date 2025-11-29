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
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.scheduling.TaskScheduler;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

@ExtendWith(MockitoExtension.class)
class WebsocketBrokerReconnectionServiceTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private StompBrokerRelayMessageHandler stompBrokerRelayMessageHandler;

    @Mock
    private Supplier<TcpOperations<byte[]>> tcpClientSupplier;

    @Mock
    private TcpOperations<byte[]> tcpClient;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private IMap<String, Boolean> brokerStatusMap;

    @Mock
    private Cluster cluster;

    @Mock
    private Member member;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    @BeforeEach
    void setUp() {
        when(hazelcastInstance.<String, Boolean>getMap(WebsocketBrokerReconnectionService.WEBSOCKET_BROKER_STATUS_MAP)).thenReturn(brokerStatusMap);
        when(hazelcastInstance.getCluster()).thenReturn(cluster);
        when(cluster.getLocalMember()).thenReturn(member);
        when(member.getUuid()).thenReturn(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));

        websocketBrokerReconnectionService = new WebsocketBrokerReconnectionService(taskScheduler, Optional.of(stompBrokerRelayMessageHandler), tcpClientSupplier,
                hazelcastInstance);
    }

    @Test
    void shouldStartReconnectAttemptsOnBrokerLoss() {
        when(tcpClientSupplier.get()).thenReturn(tcpClient);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(stompBrokerRelayMessageHandler.isRunning()).thenReturn(true);
        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));

        verify(taskScheduler).scheduleWithFixedDelay(runnableCaptor.capture(), any(Instant.class), eq(WebsocketBrokerReconnectionService.RECONNECT_INTERVAL));
        verify(stompBrokerRelayMessageHandler).stop();
        verify(tcpClientSupplier).get();
        verify(stompBrokerRelayMessageHandler).setTcpClient(tcpClient);
        verify(stompBrokerRelayMessageHandler).start();

        runnableCaptor.getValue().run();

        verify(stompBrokerRelayMessageHandler, times(2)).stop();
        verify(tcpClientSupplier, times(2)).get();
        verify(stompBrokerRelayMessageHandler, times(2)).setTcpClient(tcpClient);
        verify(stompBrokerRelayMessageHandler, times(2)).start();
    }

    @Test
    void shouldStopReconnectAttemptsWhenBrokerAvailable() {
        when(tcpClientSupplier.get()).thenReturn(tcpClient);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(stompBrokerRelayMessageHandler.isRunning()).thenReturn(true);
        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));
        verify(taskScheduler).scheduleWithFixedDelay(runnableCaptor.capture(), any(Instant.class), eq(WebsocketBrokerReconnectionService.RECONNECT_INTERVAL));

        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(true, new Object()));
        verify(scheduledFuture).cancel(false);

        runnableCaptor.getValue().run();
        verify(tcpClientSupplier, times(1)).get();
        verify(stompBrokerRelayMessageHandler, times(1)).setTcpClient(tcpClient);
        verify(stompBrokerRelayMessageHandler, times(1)).start();
    }

    @Test
    void manualReconnectSkippedWithoutRelay() {
        var serviceWithoutRelay = new WebsocketBrokerReconnectionService(taskScheduler, Optional.empty(), tcpClientSupplier, hazelcastInstance);
        assertThat(serviceWithoutRelay.triggerManualReconnect()).isFalse();
        verifyNoInteractions(tcpClientSupplier);
    }

    @Test
    void shouldNotAutoReconnectAfterManualDisconnect() {
        websocketBrokerReconnectionService.triggerManualDisconnect();

        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));

        verifyNoInteractions(tcpClientSupplier);
    }

    @Test
    void shouldAutoReconnectAfterManualDisconnectClearedByConnect() {
        websocketBrokerReconnectionService.triggerManualDisconnect();

        websocketBrokerReconnectionService.triggerManualConnect();
        when(tcpClientSupplier.get()).thenReturn(tcpClient);
        doReturn(scheduledFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        when(stompBrokerRelayMessageHandler.isRunning()).thenReturn(true);

        websocketBrokerReconnectionService.onApplicationEvent(new BrokerAvailabilityEvent(false, new Object()));

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), eq(WebsocketBrokerReconnectionService.RECONNECT_INTERVAL));
    }
}
