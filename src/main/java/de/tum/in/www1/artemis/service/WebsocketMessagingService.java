package de.tum.in.www1.artemis.service;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;

/**
 * This service sends out websocket messages.
 */
@Service
public class WebsocketMessagingService {

    SimpMessageSendingOperations messagingTemplate;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Wrapper method to send a message over websocket to the given topic
     * @param topic the destination to which subscription the message should be sent
     * @param message any object that should be sent to the destination (topic), this will typically get transformed into json
     */
    public void sendMessage(String topic, Object message) {
        messagingTemplate.convertAndSend(topic, message);
    }

    /**
     * Broadcast a new result to the client.
     *
     * @param participation the id is used in the destination (so that only clients who have subscribed the specific participation will receive the result)
     * @param result the new result that should be send to the client. It typically includes feedback, its participation will be cut off here to reduce the payload size.
     *               As the participation is already known to the client, we do not need to send it. This also cuts of the exercise (including the potentially huge
     *               problem statement and the course with all potential attributes
     */
    public void broadcastNewResult(Participation participation, Result result) {
        // remove unnecessary properties to reduce the data sent to the client (we should not send the exercise and its potentially huge problem statement)
        var originalParticipation = result.getParticipation();
        result.setParticipation(originalParticipation.copyParticipationId());

        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", result);

        // recover the participation because we might want to use it again after this method
        result.setParticipation(originalParticipation);
    }
}
