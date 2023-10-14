package de.tum.in.www1.artemis.service.iris.websocket;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * A service to send a message over the websocket to a specific user
 */
public abstract class IrisWebsocketService {

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    protected void send(User user, String sessionType, Long sessionId, Object payload) {
        String irisWebsocketTopic = String.format("%s/%s/%s", IRIS_WEBSOCKET_TOPIC_PREFIX, sessionType, sessionId);
        websocketMessagingService.sendMessageToUser(user.getLogin(), irisWebsocketTopic, payload);
    }

}
