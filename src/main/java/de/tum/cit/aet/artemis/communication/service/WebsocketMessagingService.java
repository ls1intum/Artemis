package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * This service sends out websocket messages.
 */
@Profile(PROFILE_CORE)
@Service
public class WebsocketMessagingService {

    private static final Logger log = LoggerFactory.getLogger(WebsocketMessagingService.class);

    private final SimpMessageSendingOperations messagingTemplate;

    private final Executor asyncExecutor;

    public WebsocketMessagingService(SimpMessageSendingOperations messagingTemplate, @Qualifier("taskExecutor") Executor asyncExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Sends a message over websocket to the given topic.
     * The message will be sent asynchronously.
     *
     * @param topic   the destination to which subscription the message should be sent
     * @param message a prebuild message that should be sent to the destination (topic)
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(String topic, Message<?> message) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.send(topic, message), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} to topic {}", message, topic, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a message over websocket to the given topic.
     * The message will be sent asynchronously.
     *
     * @param topic   the destination to which subscription the message should be sent
     * @param message any object that should be sent to the destination (topic), this will typically get transformed into json
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(String topic, Object message) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSend(topic, message), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} to topic {}", message, topic, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a message over websocket to the given topic to a specific user.
     * The message will be sent asynchronously.
     *
     * @param user    the user that should receive the message.
     * @param topic   the destination to send the message to
     * @param payload the payload to send
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessageToUser(String user, String topic, Object payload) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSendToUser(user, topic, payload), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} on topic {} to user {}", payload, topic, user, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
