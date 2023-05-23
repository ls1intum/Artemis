package de.tum.in.www1.artemis.service.iris;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * A service to send a message over the websocket to a specific user
 */
@Service
public class IrisWebsocketService {

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Sends a message over the websocket to a specific user
     *
     * @param irisMessage that should be send over the websocket
     */
    public void sendMessage(IrisMessage irisMessage) {
        Long irisSessionId = irisMessage.getSession().getId();
        String userLogin = irisMessage.getSession().getUser().getLogin();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
        websocketMessagingService.sendMessageToUser(userLogin, irisWebsocketTopic, irisMessage);
    }

}
