package de.tum.cit.aet.artemis.hyperion.service.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

/**
 * A service to send messages over WebSocket to users for Hyperion operations.
 */
@Lazy
@Service
@Profile(PROFILE_HYPERION)
public class HyperionWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(HyperionWebsocketService.class);

    private static final String TOPIC_PREFIX = "/topic/hyperion/";

    private final WebsocketMessagingService websocketMessagingService;

    public HyperionWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param userLogin   the login of the user
     * @param topicSuffix the suffix of the topic, which will be appended to "/topic/hyperion/"
     * @param payload     the DTO to send, which will be serialized to JSON
     */
    public void send(String userLogin, String topicSuffix, Object payload) {
        String topic = TOPIC_PREFIX + topicSuffix;
        try {
            websocketMessagingService.sendMessageToUser(userLogin, topic, payload).get();
            log.debug("Sent message to Hyperion user {} on topic {}: {}", userLogin, topic, payload);
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Error while sending message to Hyperion user {} on topic {}: {}", userLogin, topic, payload, e);
        }
    }
}
