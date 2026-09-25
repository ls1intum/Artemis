package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.websocket.GzipMessageConverter.COMPRESSION_HEADER_KEY;
import static de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics.EDITOR_SYNCHRONIZATION;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;

/**
 * Relays the synchronization events of collaborative exercise editing between the editors of an exercise.
 * <p>
 * An editor sends its changes to {@code /app/exercises/{exerciseId}/synchronization}, and this controller publishes their payload and compression header to
 * {@link ExerciseWebsocketTopics#EDITOR_SYNCHRONIZATION}. Clients never publish to the topic themselves.
 */
@Profile(PROFILE_CORE)
@Lazy
@Controller
public class ExerciseEditorSyncWebsocketController {

    private static final Logger log = LoggerFactory.getLogger(ExerciseEditorSyncWebsocketController.class);

    private final WebsocketMessagingService websocketMessagingService;

    private final SimpUserRegistry simpUserRegistry;

    public ExerciseEditorSyncWebsocketController(WebsocketMessagingService websocketMessagingService, SimpUserRegistry simpUserRegistry) {
        this.websocketMessagingService = websocketMessagingService;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Relays a synchronization event to the other editors of the exercise.
     * <p>
     * Only a session that is subscribed to the synchronization topic of the exercise may publish to it. Its subscription passed the access rule of the topic, so the check
     * needs no database access, although editors send an event for almost every keystroke. An event cannot overtake the subscription of its own session: the client
     * subscribes as soon as the connection is up and sends its queued events only afterwards, and the server registers a subscription in the user registry before it
     * reads the next frame of the same session.
     *
     * @param exerciseId the id of the exercise
     * @param message    the event as sent by the client
     * @param principal  the user who sends the event
     */
    @MessageMapping("/exercises/{exerciseId}/synchronization")
    public void relaySynchronizationEvent(@DestinationVariable long exerciseId, Message<byte[]> message, Principal principal) {
        var destination = EDITOR_SYNCHRONIZATION.at(exerciseId);
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (principal == null || !isSubscribed(principal.getName(), sessionId, destination.value())) {
            log.debug("Dropped a synchronization event for exercise {} from a session that is not subscribed to its synchronization topic", exerciseId);
            return;
        }
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        String compressed = StompHeaderAccessor.wrap(message).getFirstNativeHeader(COMPRESSION_HEADER_KEY);
        if (compressed != null) {
            headers.setNativeHeader(COMPRESSION_HEADER_KEY, compressed);
        }
        headers.setLeaveMutable(true);
        websocketMessagingService.relayMessage(destination, MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders()));
    }

    private boolean isSubscribed(String login, String sessionId, String destination) {
        SimpUser user = simpUserRegistry.getUser(login);
        SimpSession session = user != null && sessionId != null ? user.getSession(sessionId) : null;
        return session != null && session.getSubscriptions().stream().anyMatch(subscription -> destination.equals(subscription.getDestination()));
    }
}
