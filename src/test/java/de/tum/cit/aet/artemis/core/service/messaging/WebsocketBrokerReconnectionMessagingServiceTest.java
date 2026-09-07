package de.tum.cit.aet.artemis.core.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebsocketBrokerReconnectionMessagingServiceTest {

    private static final String LOCAL_NODE_ID = "01234567-89ab-cdef-0123-456789abcdef";

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private NodeRegistryService nodeRegistryService;

    @Mock
    private WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    @Mock
    private DistributedTopic<WebsocketBrokerReconnectMessage> topic;

    @Captor
    private ArgumentCaptor<Consumer<WebsocketBrokerReconnectMessage>> listenerCaptor;

    private WebsocketBrokerReconnectionMessagingService messagingService;

    @BeforeEach
    void setUp() {
        when(distributedDataProvider.<WebsocketBrokerReconnectMessage>getTopic(anyString())).thenReturn(topic);
        when(nodeRegistryService.getLocalNodeId()).thenReturn(LOCAL_NODE_ID);

        messagingService = new WebsocketBrokerReconnectionMessagingService(distributedDataProvider, nodeRegistryService, websocketBrokerReconnectionService);
        messagingService.init();
        verify(topic).addMessageListener(listenerCaptor.capture());
    }

    /**
     * Delivers a message through the captured listener, which now receives the payload directly rather than a
     * backend-specific message wrapper.
     *
     * @param message the reconnect message to deliver
     */
    private void deliver(WebsocketBrokerReconnectMessage message) {
        listenerCaptor.getValue().accept(message);
    }

    @Test
    void shouldPublishReconnectRequestWithOrigin() {
        messagingService.requestControl("target-node", "admin", WebsocketBrokerReconnectionService.ControlAction.RECONNECT);

        ArgumentCaptor<WebsocketBrokerReconnectMessage> messageCaptor = ArgumentCaptor.forClass(WebsocketBrokerReconnectMessage.class);
        verify(topic).publish(messageCaptor.capture());

        WebsocketBrokerReconnectMessage message = messageCaptor.getValue();
        assertThat(message.targetNodeId()).isEqualTo("target-node");
        assertThat(message.action()).isEqualTo(WebsocketBrokerReconnectionService.ControlAction.RECONNECT);
        assertThat(message.originatingNodeId()).isEqualTo(LOCAL_NODE_ID);
        assertThat(message.requestedBy()).isEqualTo("admin");
        assertThat(message.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldHandleReconnectForMatchingNode() {
        deliver(new WebsocketBrokerReconnectMessage(LOCAL_NODE_ID, WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin", Instant.now()));

        verify(websocketBrokerReconnectionService, times(1)).triggerManualReconnect();
    }

    @Test
    void shouldHandleReconnectForAllNodes() {
        deliver(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES, WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin",
                Instant.now()));

        verify(websocketBrokerReconnectionService, times(1)).triggerManualReconnect();
    }

    @Test
    void shouldIgnoreReconnectForDifferentNode() {
        deliver(new WebsocketBrokerReconnectMessage("some-other-node", WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin", Instant.now()));

        verify(websocketBrokerReconnectionService, never()).triggerManualReconnect();
    }

    @Test
    void shouldHandleConnectAndDisconnectActions() {
        deliver(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES, WebsocketBrokerReconnectionService.ControlAction.DISCONNECT, "admin",
                "origin", Instant.now()));
        verify(websocketBrokerReconnectionService, times(1)).triggerManualDisconnect();

        reset(websocketBrokerReconnectionService);
        deliver(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES, WebsocketBrokerReconnectionService.ControlAction.CONNECT, "admin", "origin",
                Instant.now()));
        verify(websocketBrokerReconnectionService, times(1)).triggerManualConnect();
    }
}
