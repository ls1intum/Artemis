package de.tum.in.www1.artemis.service;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;

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

    public void broadcastNewResult(Participation participation, Result result) {
        // remove some unnecessary properties to reduce the data sent to the client
        var resultParticipation = result.getParticipation();
        result.setParticipation(null);
        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", result);
        // recover the participation because we might want to use it again after this method
        result.setParticipation(resultParticipation);
    }
}
