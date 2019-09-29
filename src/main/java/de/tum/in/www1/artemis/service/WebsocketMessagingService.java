package de.tum.in.www1.artemis.service;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * This service sends out websocket messages.
 */
@Service
public class WebsocketMessagingService {

    SimpMessageSendingOperations messagingTemplate;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendMessage(String topic, Object message) {
        messagingTemplate.convertAndSend(topic, message);
    }
}
