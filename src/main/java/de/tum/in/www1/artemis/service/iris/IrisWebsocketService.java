package de.tum.in.www1.artemis.service.iris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class IrisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(IrisWebsocketService.class);

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris";

    private final WebsocketMessagingService websocketMessagingService;

    public IrisWebsocketService(WebsocketMessagingService websocketMessagingService) {
        this.websocketMessagingService = websocketMessagingService;
    }

    public void sendMessage(IrisMessage irisMessage) {
        Long irisSessionId = irisMessage.getSession().getId();
        String user = irisMessage.getSession().getUser().getLogin();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
        websocketMessagingService.sendMessageToUser(user, irisWebsocketTopic, irisMessage);
    }

}
