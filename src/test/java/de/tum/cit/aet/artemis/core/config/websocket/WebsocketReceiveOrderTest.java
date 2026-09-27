package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.broker.OrderedMessageChannelDecorator;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.ImmutableMessageChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscriptionInterceptor;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;
import de.tum.cit.aet.artemis.exercise.web.ExerciseEditorSyncWebsocketController;

class WebsocketReceiveOrderTest {

    private static final String DESTINATION = "/topic/exercises/1/synchronization";

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void deniedSubscriptionCannotAuthorizeSynchronizationWithReceiveOrdering(boolean queuedBehindEarlierFrame) {
        var fixture = new ProtocolFixture(WebsocketTopicRegistry.Decision.DENIED);
        if (queuedBehindEarlierFrame) {
            fixture.receive("UNSUBSCRIBE\nid:unrelated\n\n\0");
        }

        fixture.receive("SUBSCRIBE\nid:editor\ndestination:" + DESTINATION + "\n\n\0SEND\ndestination:/app/exercises/1/synchronization\n\nupdate\0");
        fixture.drain();

        // Spring's ordered channel reports a successful enqueue even when preSend rejected the frame.
        // Our registry must independently reject the resulting subscription event before a SEND trusts it.
        assertThat(fixture.delivered).noneMatch(message -> StompHeaderAccessor.wrap(message).getCommand() == StompCommand.SUBSCRIBE);
        assertThat(fixture.userRegistry.getUser("student").getSession("session").getSubscriptions()).isEmpty();
        verifyNoInteractions(fixture.messagingService);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void acceptedSubscriptionAuthorizesImmediatelyFollowingSend(boolean queuedBehindEarlierFrame) {
        var fixture = new ProtocolFixture(WebsocketTopicRegistry.Decision.ALLOWED);
        if (queuedBehindEarlierFrame) {
            fixture.receive("UNSUBSCRIBE\nid:unrelated\n\n\0");
        }

        fixture.receive("SUBSCRIBE\nid:editor\ndestination:" + DESTINATION + "\n\n\0SEND\ndestination:/app/exercises/1/synchronization\n\nupdate\0");
        fixture.drain();

        assertThat(fixture.delivered).extracting(message -> StompHeaderAccessor.wrap(message).getCommand()).filteredOn(command -> command != StompCommand.UNSUBSCRIBE)
                .containsExactly(StompCommand.SUBSCRIBE, StompCommand.SEND);
        verify(fixture.messagingService).relayMessage(any(), any());
    }

    @Test
    void deniedReuseOfSubscriptionIdRevokesPreviousAuthorization() {
        var fixture = new ProtocolFixture(WebsocketTopicRegistry.Decision.ALLOWED);
        fixture.receive("SUBSCRIBE\nid:editor\ndestination:" + DESTINATION + "\n\n\0");
        fixture.drain();
        assertThat(fixture.userRegistry.getUser("student").getSession("session").getSubscriptions()).hasSize(1);

        when(fixture.topicRegistry.authorizeSubscription(fixture.principal, DESTINATION)).thenReturn(WebsocketTopicRegistry.Decision.DENIED);
        fixture.receive("SUBSCRIBE\nid:editor\ndestination:" + DESTINATION + "\n\n\0SEND\ndestination:/app/exercises/1/synchronization\n\nupdate\0");
        fixture.drain();

        assertThat(fixture.userRegistry.getUser("student").getSession("session").getSubscriptions()).isEmpty();
        verifyNoInteractions(fixture.messagingService);
    }

    private static class ProtocolFixture {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        private final List<Message<?>> delivered = new ArrayList<>();

        private final DefaultSimpUserRegistry userRegistry;

        private final WebsocketMessagingService messagingService = mock(WebsocketMessagingService.class);

        private final WebsocketTopicRegistry topicRegistry = mock(WebsocketTopicRegistry.class);

        private final Principal principal = () -> "student";

        private final StompSubProtocolHandler protocol = new StompSubProtocolHandler();

        private final ExecutorSubscribableChannel inbound = new ExecutorSubscribableChannel(tasks::add);

        private final WebSocketSession session = mock(WebSocketSession.class);

        private ProtocolFixture(WebsocketTopicRegistry.Decision decision) {
            when(session.getId()).thenReturn("session");
            when(session.getPrincipal()).thenReturn(principal);
            when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
            when(session.isOpen()).thenReturn(true);

            when(topicRegistry.authorizeSubscription(principal, DESTINATION)).thenReturn(decision);
            var beans = new StaticListableBeanFactory();
            beans.addBean("websocketTopicRegistry", topicRegistry);
            var config = new WebsocketConfiguration(JsonMapper.builder().build(), mock(TaskScheduler.class), mock(TokenProvider.class),
                    beans.getBeanProvider(WebsocketTopicRegistry.class));
            userRegistry = ReflectionTestUtils.invokeMethod(config, "createLocalUserRegistry", Ordered.HIGHEST_PRECEDENCE);
            assertThat(userRegistry).isNotNull();
            var controller = new ExerciseEditorSyncWebsocketController(messagingService, userRegistry);
            inbound.addInterceptor(new WebsocketSubscriptionInterceptor(() -> topicRegistry));
            inbound.addInterceptor(new ImmutableMessageChannelInterceptor());
            OrderedMessageChannelDecorator.configureInterceptor(inbound, true);
            inbound.subscribe(message -> {
                delivered.add(message);
                if (StompHeaderAccessor.wrap(message).getCommand() == StompCommand.SEND) {
                    controller.relaySynchronizationEvent(1L, MessageBuilder.createMessage((byte[]) message.getPayload(), message.getHeaders()), principal);
                }
            });
            protocol.setPreserveReceiveOrder(true);
            protocol.setApplicationEventPublisher(event -> {
                if (event instanceof AbstractSubProtocolEvent protocolEvent) {
                    userRegistry.onApplicationEvent(protocolEvent);
                }
            });
            protocol.afterSessionStarted(session, inbound);
            receive("CONNECT\naccept-version:1.2\n\n\0");
            drain();
            delivered.clear();

            var connected = StompHeaderAccessor.create(StompCommand.CONNECTED);
            connected.setSessionId("session");
            userRegistry.onApplicationEvent(new SessionConnectedEvent(this, MessageBuilder.createMessage(new byte[0], connected.getMessageHeaders()), principal));
        }

        private void receive(String frames) {
            protocol.handleMessageFromClient(session, new TextMessage(frames), inbound);
        }

        private void drain() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }
}
