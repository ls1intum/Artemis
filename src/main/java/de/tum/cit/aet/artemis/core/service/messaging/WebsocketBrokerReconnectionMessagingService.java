package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectMessage.TARGET_ALL_NODES;

import java.time.Instant;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

import de.tum.cit.aet.artemis.core.config.websocket.WebsocketBrokerReconnectionService;

@Lazy
@Service
@Profile(PROFILE_CORE)
public class WebsocketBrokerReconnectionMessagingService {

    private static final Logger log = LoggerFactory.getLogger(WebsocketBrokerReconnectionMessagingService.class);

    private final HazelcastInstance hazelcastInstance;

    private final WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    public WebsocketBrokerReconnectionMessagingService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance,
            WebsocketBrokerReconnectionService websocketBrokerReconnectionService) {
        this.hazelcastInstance = hazelcastInstance;
        this.websocketBrokerReconnectionService = websocketBrokerReconnectionService;
    }

    @PostConstruct
    public void init() {
        ITopic<WebsocketBrokerReconnectMessage> topic = hazelcastInstance.getTopic(MessageTopic.WEBSOCKET_BROKER_RECONNECT.toString());
        topic.addMessageListener(new WebsocketReconnectListener());
    }

    /**
     * Publish a reconnect request to the Hazelcast topic. The target node will react to the message.
     *
     * @param targetNodeId hazelcast member id; use {@link WebsocketBrokerReconnectMessage#TARGET_ALL_NODES} for all nodes
     * @param requestedBy  login of the admin that initiated the request
     */
    public void requestReconnect(String targetNodeId, String requestedBy) {
        requestControl(targetNodeId, requestedBy, WebsocketBrokerReconnectionService.ControlAction.RECONNECT);
    }

    /**
     * Request a broker connect on a specific node.
     *
     * @param targetNodeId hazelcast member id or {@link WebsocketBrokerReconnectMessage#TARGET_ALL_NODES}
     * @param requestedBy  login of the admin
     */
    public void requestConnect(String targetNodeId, String requestedBy) {
        requestControl(targetNodeId, requestedBy, WebsocketBrokerReconnectionService.ControlAction.CONNECT);
    }

    /**
     * Request a broker disconnect on a specific node.
     *
     * @param targetNodeId hazelcast member id or {@link WebsocketBrokerReconnectMessage#TARGET_ALL_NODES}
     * @param requestedBy  login of the admin
     */
    public void requestDisconnect(String targetNodeId, String requestedBy) {
        requestControl(targetNodeId, requestedBy, WebsocketBrokerReconnectionService.ControlAction.DISCONNECT);
    }

    /**
     * Publish a broker control request (connect/disconnect/reconnect) to the Hazelcast topic.
     *
     * @param targetNodeId hazelcast member id; use {@link WebsocketBrokerReconnectMessage#TARGET_ALL_NODES} to target all nodes
     * @param requestedBy  login of the admin that initiated the request
     * @param action       the action to perform on the target node(s)
     */
    public void requestControl(String targetNodeId, String requestedBy, WebsocketBrokerReconnectionService.ControlAction action) {
        String originNodeId = localNodeId();
        WebsocketBrokerReconnectMessage message = new WebsocketBrokerReconnectMessage(targetNodeId, action, requestedBy, originNodeId, Instant.now());

        try {
            hazelcastInstance.<WebsocketBrokerReconnectMessage>getTopic(MessageTopic.WEBSOCKET_BROKER_RECONNECT.toString()).publish(message);
        }
        catch (Exception ex) {
            log.warn("Failed to publish websocket broker reconnect request to Hazelcast: {}", ex.getMessage(), ex);
            if (shouldHandleLocally(targetNodeId)) {
                switch (action) {
                    case CONNECT -> websocketBrokerReconnectionService.triggerManualConnect();
                    case DISCONNECT -> websocketBrokerReconnectionService.triggerManualDisconnect();
                    default -> websocketBrokerReconnectionService.triggerManualReconnect();
                }
            }
        }
    }

    String localNodeId() {
        Member localMember = hazelcastInstance.getCluster().getLocalMember();
        return localMember.getUuid().toString();
    }

    boolean shouldHandleLocally(String targetNodeId) {
        return TARGET_ALL_NODES.equalsIgnoreCase(targetNodeId) || localNodeId().equals(targetNodeId);
    }

    class WebsocketReconnectListener implements MessageListener<WebsocketBrokerReconnectMessage> {

        @Override
        public void onMessage(Message<WebsocketBrokerReconnectMessage> message) {
            handleReconnectMessage(message.getMessageObject());
        }

        void handleReconnectMessage(WebsocketBrokerReconnectMessage reconnectMessage) {
            if (shouldHandleLocally(reconnectMessage.targetNodeId())) {
                log.info("Received websocket broker {} request from node {} (requested by {}), triggering on this node", reconnectMessage.action(),
                        reconnectMessage.originatingNodeId(), reconnectMessage.requestedBy());
                switch (reconnectMessage.action()) {
                    case CONNECT -> websocketBrokerReconnectionService.triggerManualConnect();
                    case DISCONNECT -> websocketBrokerReconnectionService.triggerManualDisconnect();
                    default -> websocketBrokerReconnectionService.triggerManualReconnect();
                }
            }
            else {
                log.debug("Ignoring websocket broker reconnect request for node {} (this node is {})", reconnectMessage.targetNodeId(), localNodeId());
            }
        }
    }
}
