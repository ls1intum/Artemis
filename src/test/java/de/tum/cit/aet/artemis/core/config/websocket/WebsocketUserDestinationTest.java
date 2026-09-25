package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.OrderedMessageChannelDecorator;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.ImmutableMessageChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;

class WebsocketUserDestinationTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void sendsPersonalUpdatesToEverySessionWithReceiveOrderingEnabled(boolean forwardedFromAnotherNode) {
        var config = new WebsocketConfiguration(JsonMapper.builder().build(), mock(TaskScheduler.class), mock(TokenProvider.class),
                new StaticListableBeanFactory().getBeanProvider(WebsocketTopicRegistry.class));
        ReflectionTestUtils.setField(config, "brokerAddresses", List.of());
        var inbound = new ExecutorSubscribableChannel();
        OrderedMessageChannelDecorator.configureInterceptor(inbound, true);
        var broker = new ExecutorSubscribableChannel();
        broker.addInterceptor(new ImmutableMessageChannelInterceptor());
        List<Message<?>> delivered = new ArrayList<>();
        broker.subscribe(delivered::add);
        var handler = config.userDestinationMessageHandler(inbound, new ExecutorSubscribableChannel(), broker,
                message -> "/user/student/topic/newResults".equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))
                        ? new UserDestinationResult("/user/student/topic/newResults", Set.of("/topic/newResults-user1", "/topic/newResults-user2"), "/user/topic/newResults",
                                "student", Set.of("session-1", "session-2"))
                        : null);
        handler.setBroadcastDestination("/topic/unresolved-user");
        var headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headers.setDestination("/user/student/topic/newResults");

        if (forwardedFromAnotherNode) {
            headers.setDestination("/topic/unresolved-user");
            headers.setSessionId("_system_");
            headers.setNativeHeader(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION, "/user/student/topic/newResults");
        }
        handler.start();
        try {
            inbound.send(MessageBuilder.createMessage("result", headers.getMessageHeaders()));
        }
        finally {
            handler.stop();
        }

        assertThat(delivered).hasSize(2);
        assertThat(delivered).extracting(message -> SimpMessageHeaderAccessor.getDestination(message.getHeaders())).containsExactlyInAnyOrder("/topic/newResults-user1",
                "/topic/newResults-user2");
        assertThat(delivered).allSatisfy(message -> assertThat(message.getPayload()).isEqualTo("result"));
        assertThat(OrderedMessageChannelDecorator.supportsOrderedMessages(inbound)).isTrue();
    }
}
