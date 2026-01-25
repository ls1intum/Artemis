package de.tum.cit.aet.artemis.core.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebsocketBrokerReconnectionMessagingServiceTest {

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    @Mock
    private ITopic<WebsocketBrokerReconnectMessage> topic;

    @Mock
    private Cluster cluster;

    @Mock
    private Member member;

    @Mock
    private Message<WebsocketBrokerReconnectMessage> hazelcastMessage;

    @Captor
    private ArgumentCaptor<MessageListener<WebsocketBrokerReconnectMessage>> listenerCaptor;

    private WebsocketBrokerReconnectionMessagingService messagingService;

    @BeforeEach
    void setUp() {
        when(hazelcastInstance.<WebsocketBrokerReconnectMessage>getTopic(MessageTopic.WEBSOCKET_BROKER_RECONNECT.toString())).thenReturn(topic);
        when(hazelcastInstance.getCluster()).thenReturn(cluster);
        when(cluster.getLocalMember()).thenReturn(member);
        when(member.getUuid()).thenReturn(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));

        messagingService = new WebsocketBrokerReconnectionMessagingService(hazelcastInstance, websocketBrokerReconnectionService);
        messagingService.init();
        verify(topic).addMessageListener(listenerCaptor.capture());
    }

    @Test
    void shouldPublishReconnectRequestWithOrigin() {
        messagingService.requestControl("target-node", "admin", WebsocketBrokerReconnectionService.ControlAction.RECONNECT);

        ArgumentCaptor<WebsocketBrokerReconnectMessage> messageCaptor = ArgumentCaptor.forClass(WebsocketBrokerReconnectMessage.class);
        verify(topic).publish(messageCaptor.capture());

        WebsocketBrokerReconnectMessage message = messageCaptor.getValue();
        assertThat(message.targetNodeId()).isEqualTo("target-node");
        assertThat(message.action()).isEqualTo(WebsocketBrokerReconnectionService.ControlAction.RECONNECT);
        assertThat(message.originatingNodeId()).isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
        assertThat(message.requestedBy()).isEqualTo("admin");
        assertThat(message.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldHandleReconnectForMatchingNode() {
        when(hazelcastMessage.getMessageObject()).thenReturn(new WebsocketBrokerReconnectMessage("01234567-89ab-cdef-0123-456789abcdef",
                WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin", Instant.now()));

        listenerCaptor.getValue().onMessage(hazelcastMessage);

        verify(websocketBrokerReconnectionService, times(1)).triggerManualReconnect();
    }

    @Test
    void shouldHandleReconnectForAllNodes() {
        when(hazelcastMessage.getMessageObject()).thenReturn(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES,
                WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin", Instant.now()));

        listenerCaptor.getValue().onMessage(hazelcastMessage);

        verify(websocketBrokerReconnectionService, times(1)).triggerManualReconnect();
    }

    @Test
    void shouldIgnoreReconnectForDifferentNode() {
        when(hazelcastMessage.getMessageObject())
                .thenReturn(new WebsocketBrokerReconnectMessage("some-other-node", WebsocketBrokerReconnectionService.ControlAction.RECONNECT, "admin", "origin", Instant.now()));

        listenerCaptor.getValue().onMessage(hazelcastMessage);

        verify(websocketBrokerReconnectionService, never()).triggerManualReconnect();
    }

    @Test
    void shouldHandleConnectAndDisconnectActions() {
        when(hazelcastMessage.getMessageObject()).thenReturn(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES,
                WebsocketBrokerReconnectionService.ControlAction.DISCONNECT, "admin", "origin", Instant.now()));

        listenerCaptor.getValue().onMessage(hazelcastMessage);
        verify(websocketBrokerReconnectionService, times(1)).triggerManualDisconnect();

        reset(websocketBrokerReconnectionService);
        when(hazelcastMessage.getMessageObject()).thenReturn(new WebsocketBrokerReconnectMessage(WebsocketBrokerReconnectMessage.TARGET_ALL_NODES,
                WebsocketBrokerReconnectionService.ControlAction.CONNECT, "admin", "origin", Instant.now()));

        listenerCaptor.getValue().onMessage(hazelcastMessage);
        verify(websocketBrokerReconnectionService, times(1)).triggerManualConnect();
    }
}
