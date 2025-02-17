package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.websocket.GzipMessageConverter.COMPRESSION_HEADER;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

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
     * @param payload the payload to send in the message (e.g. a record DTO), which will be transformed into json and potentially compressed
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessage(String topic, Object payload) {
        try {
            Map<String, Object> headers = shouldCompress(topic, payload) ? COMPRESSION_HEADER : Map.of();
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSend(topic, payload, headers), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending payload {} to topic {}", payload, topic, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Sends a message over websocket to the given topic to a specific user.
     * The message will be sent asynchronously.
     *
     * @param user    the user that should receive the message.
     * @param topic   the destination to send the message to
     * @param payload the payload to send in the message (e.g. a record DTO), which will be transformed into json and potentially compressed
     * @return a future that can be used to check if the message was sent successfully or resulted in an exception
     */
    public CompletableFuture<Void> sendMessageToUser(String user, String topic, Object payload) {
        try {
            Map<String, Object> headers = shouldCompress(topic, payload) ? COMPRESSION_HEADER : Map.of();
            return CompletableFuture.runAsync(() -> messagingTemplate.convertAndSendToUser(user, topic, payload, headers), asyncExecutor);
        }
        // Note: explicitly catch ALL kinds of exceptions here and do NOT rethrow, because the actual task should NEVER be interrupted when the server cannot send WS messages
        catch (Exception ex) {
            log.error("Error when sending payload {} on topic {} to user {}", payload, topic, user, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * A regex pattern to match compressible WebSocket topics.
     * <p>
     * The topics covered by this pattern are:
     * 1. Topics for course-specific job statuses:
     * - `/topic/courses/{courseId}/queued-jobs`
     * - `/topic/courses/{courseId}/running-jobs`
     * - `{courseId}` is a numeric identifier (long).
     * <p>
     * 2. Topics for admin-level job statuses and build agents:
     * - `/topic/admin/queued-jobs`
     * - `/topic/admin/running-jobs`
     * - `/topic/admin/build-agents`
     * <p>
     * 3. Topics for specific build agent details:
     * - `/topic/admin/build-agent/{buildAgentName}`
     * - `{buildAgentName}` is a string that does not contain a forward slash (`/`).
     * <p>
     * Regex Details:
     * - `^/topic/courses/\\d+/(queued-jobs|running-jobs)`:
     * Matches topics for course-specific jobs with a numeric `courseId`.
     * - `|^/topic/admin/(queued-jobs|running-jobs|build-agents)`:
     * Matches admin-level job and build agent topics.
     * - `|^/topic/admin/build-agent/[^/]+$`:
     * Matches specific build agent topics, where `{buildAgentName}` is any string excluding `/`.
     */
    private static final Pattern COMPRESSIBLE_TOPICS = Pattern
            .compile("^/topic/courses/\\d+/(queued-jobs|running-jobs)|" + "^/topic/admin/(queued-jobs|running-jobs|build-agents)|" + "^/topic/admin/build-agent/[^/]+$");

    /**
     * Determine if a message for a specific topic should be compressed.
     */
    private static boolean shouldCompress(String topic, Object payload) {
        // Only compress messages for specific topics
        if (topic == null) {
            return false;
        }
        if (isEmpty(payload)) {
            return false;
        }
        // Match the topic against the regex
        return COMPRESSIBLE_TOPICS.matcher(topic).matches();
    }

    private static boolean isEmpty(Object payload) {
        return payload == null || payload.toString().isEmpty() || (payload instanceof Collection<?> collection && collection.isEmpty())
                || (payload instanceof Map<?, ?> map && map.isEmpty());
    }
}
