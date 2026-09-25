package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscriptionInterceptor;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks that every client frame passes both Spring Security's frame-level rules and the topic subscription check, and what those frame-level rules allow.
 */
class WebsocketSecurityConfigurationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    @Qualifier("clientInboundChannel")
    private AbstractSubscribableChannel clientInboundChannel;

    @Autowired
    private SimpAnnotationMethodMessageHandler annotationMethodMessageHandler;

    @Autowired
    private AuthorizationManager<Message<?>> authorizationManager;

    @Autowired
    private SubProtocolWebSocketHandler subProtocolWebSocketHandler;

    @Test
    void testInboundChannelAppliesSpringSecurityAndTheSubscriptionCheck() {
        List<Class<?>> interceptorTypes = clientInboundChannel.getInterceptors().stream().<Class<?>>map(ChannelInterceptor::getClass).toList();
        assertThat(interceptorTypes).contains(AuthorizationChannelInterceptor.class, WebsocketSubscriptionInterceptor.class);
        // Spring Security authorizes the frame before the subscription check looks at its destination
        assertThat(interceptorTypes.indexOf(AuthorizationChannelInterceptor.class)).isLessThan(interceptorTypes.indexOf(WebsocketSubscriptionInterceptor.class));
    }

    @Test
    void testClientMessagesOnlyReachMessageHandlers() {
        assertThat(annotationMethodMessageHandler.getDestinationPrefixes()).containsExactly("/app/");
    }

    @Test
    void testRejectedFramesAreDroppedWithoutClosingTheConnection() {
        var stompHandler = subProtocolWebSocketHandler.getProtocolHandlers().stream().filter(StompSubProtocolHandler.class::isInstance).map(StompSubProtocolHandler.class::cast)
                .findFirst().orElseThrow();
        var errorHandler = stompHandler.getErrorHandler();
        assertThat(errorHandler).isNotNull();
        var denied = new MessageDeliveryException(frame(StompCommand.SEND, "/topic/x"), "denied", new AccessDeniedException("denied"));

        // no ERROR frame, so the connection stays open
        assertThat(errorHandler.handleClientMessageProcessingError(frame(StompCommand.SEND, "/topic/participations/1/team/trigger"), denied)).isNull();
        assertThat(errorHandler.handleClientMessageProcessingError(frame(StompCommand.SUBSCRIBE, "/queue/anything"), denied)).isNull();
        // everything else keeps the default ERROR frame
        assertThat(errorHandler.handleClientMessageProcessingError(frame(StompCommand.CONNECT, null), denied)).isNotNull();
        assertThat(errorHandler.handleClientMessageProcessingError(frame(StompCommand.SEND, "/app/iris/command-ack"), new IllegalStateException("broken"))).isNotNull();
    }

    private static Message<byte[]> frame(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void testFrameLevelRules() {
        var user = new UsernamePasswordAuthenticationToken("student", "irrelevant", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        var anonymous = new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority())));

        assertThat(isGranted(user, StompCommand.CONNECT, null)).isTrue();
        assertThat(isGranted(anonymous, StompCommand.CONNECT, null)).isFalse();
        assertThat(isGranted(user, StompCommand.UNSUBSCRIBE, null)).isTrue();

        assertThat(isGranted(user, StompCommand.SUBSCRIBE, "/topic/management/feature-toggles")).isTrue();
        assertThat(isGranted(user, StompCommand.SUBSCRIBE, "/user/topic/newResults")).isTrue();
        assertThat(isGranted(anonymous, StompCommand.SUBSCRIBE, "/topic/management/feature-toggles")).isFalse();
        assertThat(isGranted(user, StompCommand.SUBSCRIBE, "/queue/anything")).isFalse();
        assertThat(isGranted(user, StompCommand.SUBSCRIBE, "/app/iris/command-ack")).isFalse();

        assertThat(isGranted(user, StompCommand.SEND, "/app/iris/command-ack")).isTrue();
        assertThat(isGranted(anonymous, StompCommand.SEND, "/app/iris/command-ack")).isFalse();
        for (String brokerDestination : List.of("/topic/notification/system-notification", "/topic/exercises/1/synchronization", "/user/student/topic/newResults",
                "/topic/unresolved-user")) {
            assertThat(isGranted(user, StompCommand.SEND, brokerDestination)).as("client message to %s", brokerDestination).isFalse();
        }
    }

    private boolean isGranted(Authentication authentication, StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(authentication);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var result = authorizationManager.authorize(() -> authentication, message);
        return result != null && result.isGranted();
    }
}
