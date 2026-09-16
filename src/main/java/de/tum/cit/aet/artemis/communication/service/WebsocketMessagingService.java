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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * This service sends out websocket messages.
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
     * <p>
     * The topic is matched first because it rejects almost everything: only the build queue and build agent topics are
     * compressible, and every other message the server sends - submissions, results, exam events, notifications - is
     * decided by the regex alone. Asking about the payload first made every one of those pay for a question whose
     * answer could not matter.
     */
    private static boolean shouldCompress(String topic, Object payload) {
        // Only compress messages for specific topics
        if (topic == null) {
            return false;
        }
        // Match the topic against the regex
        if (!COMPRESSIBLE_TOPICS.matcher(topic).matches()) {
            return false;
        }
        return !isEmpty(payload);
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
