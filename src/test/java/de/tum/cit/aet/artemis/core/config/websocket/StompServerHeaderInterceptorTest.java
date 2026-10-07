package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;

class StompServerHeaderInterceptorTest {

    private static final String BROKER_CONNECTED_FRAME = "CONNECTED\nversion:1.2\nheart-beat:10000,10000\nserver:ActiveMQ-Artemis/2.41.0 ActiveMQ Artemis Messaging Engine\nsession:abc\n\n\0";

    private final StompServerHeaderInterceptor interceptor = new StompServerHeaderInterceptor();

    private final MessageChannel channel = mock(MessageChannel.class);

    private final WebSocketSession session = mock(WebSocketSession.class);

    private final StompSubProtocolHandler protocol = new StompSubProtocolHandler();

    @BeforeEach
    void setUp() {
        Principal principal = () -> "student";
        when(session.getId()).thenReturn("session");
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenReturn(true);
        protocol.afterSessionStarted(session, new ExecutorSubscribableChannel());
    }

    @Test
    void connectedFrameOfTheBrokerReachesTheClientWithoutItsServerHeader() throws Exception {
        Message<?> filtered = interceptor.preSend(decode(BROKER_CONNECTED_FRAME), channel);

        protocol.handleMessageToClient(session, castToByteArrayMessage(filtered));

        String frame = sentFrame();
        assertThat(frame).startsWith("CONNECTED\n").doesNotContain("server:").doesNotContainIgnoringCase("activemq").doesNotContain("2.41.0");
        // everything else the client needs to set up the connection stays
        assertThat(frame).contains("version:1.2").contains("heart-beat:");
    }

    @Test
    void connectedFrameOfTheBrokerCarriesTheServerHeaderWithoutTheInterceptor() throws Exception {
        // guards the test above: without the interceptor, the same frame carries the header
        protocol.handleMessageToClient(session, castToByteArrayMessage(decode(BROKER_CONNECTED_FRAME)));

        assertThat(sentFrame()).contains("server:ActiveMQ-Artemis/2.41.0");
    }

    @Test
    void removesTheHeaderFromAnImmutableFrame() {
        Message<byte[]> frame = decode(BROKER_CONNECTED_FRAME);
        StompHeaderAccessor.getAccessor(frame, StompHeaderAccessor.class).setImmutable();

        Message<?> filtered = interceptor.preSend(frame, channel);

        assertThat(StompHeaderAccessor.wrap(filtered).getFirstNativeHeader("server")).isNull();
        // the received frame itself is untouched
        assertThat(StompHeaderAccessor.wrap(frame).getFirstNativeHeader("server")).isEqualTo("ActiveMQ-Artemis/2.41.0 ActiveMQ Artemis Messaging Engine");
    }

    @Test
    void leavesConnectedFrameWithoutServerHeaderUntouched() {
        Message<byte[]> frame = decode("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void leavesOtherFramesUntouched() {
        // a MESSAGE may legitimately carry a header called server
        Message<byte[]> frame = decode("MESSAGE\ndestination:/topic/test\nsubscription:sub-0\nmessage-id:1\nserver:keep-me\n\nbody\0");

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        assertThat(StompHeaderAccessor.wrap(frame).getCommand()).isEqualTo(StompCommand.MESSAGE);
    }

    @Test
    void ignoresMessagesWithoutStompHeaders() {
        Message<String> frame = MessageBuilder.withPayload("plain").build();

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void isRegisteredOnTheClientOutboundChannel() {
        // the relay hands the CONNECTED frame of the broker to this channel, so the interceptor has to be part of its configuration
        var registration = new ChannelRegistration();
        var config = new WebsocketConfiguration(JsonMapper.builder().build(), mock(TaskScheduler.class), mock(TokenProvider.class),
                new StaticListableBeanFactory().getBeanProvider(WebsocketTopicRegistry.class), "http://localhost", new ArtemisProperties());

        config.configureClientOutboundChannel(registration);

        assertThat((List<?>) ReflectionTestUtils.invokeMethod(registration, "getInterceptors")).anyMatch(StompServerHeaderInterceptor.class::isInstance);
    }

    private static Message<byte[]> decode(String frame) {
        return new StompDecoder().decode(ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8))).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Message<byte[]> castToByteArrayMessage(Message<?> message) {
        return (Message<byte[]>) message;
    }

    private String sentFrame() throws Exception {
        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(sent.capture());
        return ((TextMessage) sent.getValue()).getPayload();
    }
}
