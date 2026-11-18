package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.config.websocket.GzipMessageConverter;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.exercise.web.ExerciseEditorSyncWebsocketController;

class ExerciseEditorSyncWebsocketControllerTest {

    private static final long EXERCISE_ID = 42L;

    private WebsocketMessagingService websocketMessagingService;

    private SimpUserRegistry simpUserRegistry;

    private ExerciseEditorSyncWebsocketController controller;

    private final Principal editor = () -> "editor1";

    @BeforeEach
    void setUp() {
        websocketMessagingService = mock(WebsocketMessagingService.class);
        simpUserRegistry = mock(SimpUserRegistry.class);
        controller = new ExerciseEditorSyncWebsocketController(websocketMessagingService, simpUserRegistry);
    }

    @Test
    void testRelaysEventsOfSubscribedSessionsUnchanged() {
        sessionWithSubscriptions("session-1", "/topic/exercises/" + EXERCISE_ID + "/synchronization");
        byte[] payload = "compressed-payload".getBytes(StandardCharsets.UTF_8);

        controller.relaySynchronizationEvent(EXERCISE_ID, clientMessage("session-1", payload, true), editor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<?>> relayed = ArgumentCaptor.forClass(Message.class);
        verify(websocketMessagingService).relayMessage(topic("/topic/exercises/" + EXERCISE_ID + "/synchronization"), relayed.capture());
        assertThat(relayed.getValue().getPayload()).isEqualTo(payload);
        assertThat(StompHeaderAccessor.wrap(relayed.getValue()).getFirstNativeHeader(GzipMessageConverter.COMPRESSION_HEADER_KEY)).isEqualTo("true");
    }

    @Test
    void testDropsEventsOfSessionsWithoutSubscription() {
        sessionWithSubscriptions("session-1", "/topic/exercises/" + (EXERCISE_ID + 1) + "/synchronization");

        controller.relaySynchronizationEvent(EXERCISE_ID, clientMessage("session-1", new byte[0], false), editor);
        controller.relaySynchronizationEvent(EXERCISE_ID, clientMessage("unknown-session", new byte[0], false), editor);
        controller.relaySynchronizationEvent(EXERCISE_ID, clientMessage("session-1", new byte[0], false), null);

        verify(websocketMessagingService, never()).relayMessage(any(WebsocketDestination.class), any(Message.class));
    }

    private void sessionWithSubscriptions(String sessionId, String destination) {
        SimpUser user = mock(SimpUser.class);
        SimpSession session = mock(SimpSession.class);
        SimpSubscription subscription = mock(SimpSubscription.class);
        when(simpUserRegistry.getUser("editor1")).thenReturn(user);
        when(user.getSession(sessionId)).thenReturn(session);
        when(session.getSubscriptions()).thenReturn(Set.of(subscription));
        when(subscription.getDestination()).thenReturn(destination);
    }

    private static Message<byte[]> clientMessage(String sessionId, byte[] payload, boolean compressed) {
        var headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/app/exercises/" + EXERCISE_ID + "/synchronization");
        headers.setSessionId(sessionId);
        if (compressed) {
            headers.setNativeHeader(GzipMessageConverter.COMPRESSION_HEADER_KEY, "true");
        }
        return MessageBuilder.createMessage(payload, headers.getMessageHeaders());
    }
}
