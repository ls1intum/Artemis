package de.tum.in.www1.artemis.service.iris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;

@Service
public class IrisWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(IrisWebsocketService.class);

    private static final String IRIS_WEBSOCKET_TOPIC_PREFIX = "/topic/iris/";

    private final SimpMessageSendingOperations messagingTemplate;

    public IrisWebsocketService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastMessage(IrisMessage irisMessage) {
        Long irisSessionId = irisMessage.getSession().getId();
        String irisWebsocketTopic = String.format("%s/sessions/%d", IRIS_WEBSOCKET_TOPIC_PREFIX, irisSessionId);
        messagingTemplate.convertAndSend(irisWebsocketTopic, irisMessage);
    }

}
