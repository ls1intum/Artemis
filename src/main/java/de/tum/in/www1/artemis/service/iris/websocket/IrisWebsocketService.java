package de.tum.in.www1.artemis.service.iris.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * A service to send a message over the websocket to a specific user
 */
public class IrisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(IrisWebsocketService.class);

    private static final String TOPIC_PREFIX = "/topic/iris/";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public void send(User user, String topicSuffix, Object payload) {
        String topic = TOPIC_PREFIX + topicSuffix;
        log.debug("Sending message to Iris user {} on topic {}: {}", user.getLogin(), topic, payload);
        websocketMessagingService.sendMessageToUser(user.getLogin(), topic, payload);
    }

}
