package de.tum.in.www1.artemis.service.iris.websocket;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * A service to send a message over the websocket to a specific user
 */
@Service
@Profile("iris")
public class IrisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(IrisWebsocketService.class);

    private static final String TOPIC_PREFIX = "/topic/iris/";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param userLogin   the login of the user
     * @param topicSuffix the suffix of the topic, which will be appended to "/topic/iris/"
     * @param payload     the DTO to send, which will be serialized to JSON
     */
    public void send(String userLogin, String topicSuffix, Object payload) {
        String topic = TOPIC_PREFIX + topicSuffix;
        try {
            websocketMessagingService.sendMessageToUser(userLogin, topic, payload).get();
            log.debug("Sent message to Iris user {} on topic {}: {}", userLogin, topic, payload);
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Error while sending message to Iris user {} on topic {}: {}", userLogin, topic, payload, e);
        }
    }

}
