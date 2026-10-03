package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;

/**
 * Covers how {@link ParticipationTeamWebsocketService} reads the headers of a subscribe event, which needs neither a Spring context nor any persisted exercise.
 */
class ParticipationTeamWebsocketServiceConcurrencyTest {

    private static final String DESTINATION = "/topic/participations/42/team";

    private static final String SESSION_ID = "concurrent-session";

    private static final String SUBSCRIPTION_ID = "concurrent-subscription";

    private SimpUserRegistry simpUserRegistry;

    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    private final Principal subscriber = () -> "student1";

    @BeforeEach
    void setUp() {
        simpUserRegistry = mock(SimpUserRegistry.class);
        participationTeamWebsocketService = new ParticipationTeamWebsocketService(mock(WebsocketMessagingService.class), simpUserRegistry, null, null, null, Optional.empty(),
                Optional.empty(), null);
    }

    @Test
    void testHandlesSubscriptionEventsWhileTheFrameHeadersAreStillModified() throws InterruptedException {
        // Spring publishes the subscribe event after it handed the frame to the inbound channel, so a handler still adds headers to the very same mutable map on
        // another thread, e.g. AbstractMethodMessageHandler adds the lookup destination. A listener that copies those headers loses that race and fails with a
        // ConcurrentModificationException, which is why the headers have to be read in place.
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setLeaveMutable(true);
        headers.setSessionId(SESSION_ID);
        headers.setSubscriptionId(SUBSCRIPTION_ID);
        headers.setDestination(DESTINATION);
        headers.setUser(subscriber);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var event = new SessionSubscribeEvent(this, message, subscriber);
        // The registry knows the session, but for a different topic, so the listener reads all three headers and still stops before it announces the subscriber.
        registerSession("/topic/participations/43/team");

        AtomicBoolean keepModifying = new AtomicBoolean(true);
        Thread modifier = new Thread(() -> {
            // Adding and removing a header keeps modifying the map structurally without letting it grow.
            for (int i = 0; keepModifying.get(); i++) {
                MessageHeaderAccessor accessor = MessageHeaderAccessor.getMutableAccessor(message);
                accessor.setHeader("probeHeader" + (i % 16), i);
                accessor.setHeader("probeHeader" + ((i + 1) % 16), null);
            }
        });
        modifier.setDaemon(true);
        modifier.start();

        try {
            assertThatCode(() -> {
                for (int i = 0; i < 2000; i++) {
                    participationTeamWebsocketService.handleSubscribe(event);
                }
            }).doesNotThrowAnyException();
        }
        finally {
            keepModifying.set(false);
            modifier.join(5000);
        }
    }

    private void registerSession(String subscribedDestination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscription.getDestination()).thenReturn(subscribedDestination);
        SimpSession session = mock(SimpSession.class);
        when(session.getSubscriptions()).thenReturn(Set.copyOf(List.of(subscription)));
        SimpUser user = mock(SimpUser.class);
        when(user.getSession(SESSION_ID)).thenReturn(session);
        when(simpUserRegistry.getUser(subscriber.getName())).thenReturn(user);
    }
}
