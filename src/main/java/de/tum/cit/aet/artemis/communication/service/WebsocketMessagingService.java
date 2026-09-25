package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.websocket.GzipMessageConverter.COMPRESSION_HEADER;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;

/**
 * This service sends out websocket messages. It only accepts destinations of declared topics: a {@link WebsocketTopic} carries the rule who may subscribe, a
 * {@link WebsocketUserTopic} is delivered to a single user. See {@code documentation/docs/developer/guidelines/websocket.mdx}.
 */
@Profile(PROFILE_CORE)
@Lazy
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
     * Sends a prebuilt message to all subscribers of a declared topic. The message is sent asynchronously.
     *
     * @param destination the destination of a declared {@link WebsocketTopic}
     * @param message     a prebuilt message
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(WebsocketDestination destination, Message<?> message) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.send(destination.value(), message), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending message {} to topic {}", message, destination, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a payload to all subscribers of a declared topic. The message is sent asynchronously.
     *
     * @param destination the destination of a declared {@link WebsocketTopic}; who may subscribe to it is declared with the topic
     * @param payload     the payload to send in the message (e.g. a record DTO), which will be transformed into json and compressed if the topic asks for it
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(WebsocketDestination destination, Object payload) {
        try {
            Map<String, Object> headers = destination.topic().isCompressed() && !isEmpty(payload) ? COMPRESSION_HEADER : Map.of();
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSend(destination.value(), payload, headers), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending payload {} to topic {}", payload, destination, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a payload to the sessions of one user on a declared per-user topic. The message is sent asynchronously.
     *
     * @param login       the login of the user that should receive the message
     * @param destination the destination of a declared {@link WebsocketUserTopic}
     * @param payload     the payload to send in the message (e.g. a record DTO), which will be transformed into json
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessageToUser(String login, WebsocketUserDestination destination, Object payload) {
        try {
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSendToUser(login, destination.value(), payload), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending payload {} on topic {} to user {}", payload, destination, login, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Whether there is nothing worth compressing.
     * <p>
     * Deliberately does not call {@code toString()} on an arbitrary payload. Doing so rendered whole DTO graphs to a
     * String only to ask whether the String was empty and then discard it: profiling a 1000-student exam found 4.3% of
     * the Artemis nodes' on-CPU samples inside this method, almost all of it in {@code StringBuilder.append} and
     * integer-to-text conversion under {@code BuildJobQueueItem.toString} and {@code BuildConfig.toString}.
     * <p>
     * A DTO's {@code toString()} is never empty, so the check only ever meant anything for a text payload, and that is
     * what it now asks about.
     *
     * @param payload the message payload
     * @return true if the payload carries nothing
     */
    private static boolean isEmpty(Object payload) {
        return switch (payload) {
            case null -> true;
            case CharSequence text -> text.isEmpty();
            case Collection<?> collection -> collection.isEmpty();
            case Map<?, ?> map -> map.isEmpty();
            default -> false;
        };
    }
}
