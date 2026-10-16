package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.ALLOWED;
import static de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry.Decision.DENIED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscriptionInterceptor;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.web.IrisCommandWebsocketController;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks that every client frame passes both Spring Security's frame-level rules and the topic subscription check, and what those frame-level rules allow.
 */
class WebsocketSecurityConfigurationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "wssecurityconfig";

    @Autowired
    @Qualifier("clientInboundChannel")
    private AbstractSubscribableChannel clientInboundChannel;

    @Autowired
    private SimpAnnotationMethodMessageHandler annotationMethodMessageHandler;

    @Autowired
    private AuthorizationManager<Message<?>> authorizationManager;

    @Autowired
    private SubProtocolWebSocketHandler subProtocolWebSocketHandler;

    @Autowired
    private WebsocketTopicRegistry registry;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

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

    @Test
    void testClientFramesPreserveReceiveOrderWithinEachSession() {
        var stompHandler = subProtocolWebSocketHandler.getProtocolHandlers().stream().filter(StompSubProtocolHandler.class::isInstance).map(StompSubProtocolHandler.class::cast)
                .findFirst().orElseThrow();
        assertThat(stompHandler.isPreserveReceiveOrder()).isTrue();
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
                "/topic/unresolved-user", "/topic/quizExercise/42/other")) {
            assertThat(isGranted(user, StompCommand.SEND, brokerDestination)).as("client message to %s", brokerDestination).isFalse();
            // a MESSAGE frame of a client is a message as well, which the broker would forward like one of the server
            assertThat(isGranted(user, StompCommand.MESSAGE, brokerDestination)).as("client MESSAGE frame to %s", brokerDestination).isFalse();
        }

        // the quiz answers of the Android app, which the broker acknowledges but delivers to no one
        assertThat(isGranted(user, StompCommand.SEND, "/topic/quizExercise/42/submission")).isTrue();
        assertThat(isGranted(anonymous, StompCommand.SEND, "/topic/quizExercise/42/submission")).isFalse();
    }

    private boolean isGranted(Authentication authentication, StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(authentication);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var result = authorizationManager.authorize(() -> authentication, message);
        return result != null && result.isGranted();
    }

    @Test
    void testIrisCommandAckReachesItsHandlerOnlyForAuthenticatedUsers() {
        var user = new UsernamePasswordAuthenticationToken("student", "irrelevant", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        // The acknowledgement of a client reaches IrisCommandWebsocketController, which attributes it to the sending principal
        assertThat(annotationMethodMessageHandler.getHandlerMethods().entrySet()).anySatisfy(mapping -> {
            assertThat(mapping.getValue().getBeanType()).isEqualTo(IrisCommandWebsocketController.class);
            assertThat(mapping.getKey().getDestinationConditions().getPatterns()).containsExactly("/iris/command-ack");
        });
        assertThat(isGranted(user, StompCommand.SEND, "/app/iris/command-ack")).isTrue();
        // The former broker destination of the acknowledgement and every server-owned Iris feed stay closed to client messages
        for (String destination : List.of("/topic/iris/command-ack", "/topic/iris/session/7", "/user/topic/iris/competencies/42", "/user/topic/iris/7")) {
            assertThat(isGranted(user, StompCommand.SEND, destination)).as("client message to %s", destination).isFalse();
            assertThat(isGranted(user, StompCommand.MESSAGE, destination)).as("client MESSAGE frame to %s", destination).isFalse();
        }
    }

    /**
     * Server-owned course and personal feeds: a client must never publish to them, because the broker would deliver the message as if the server had sent it.
     */
    @ParameterizedTest
    @ValueSource(strings = { "/topic/atlas/orchestrator/42", "/topic/courses/42/quizExercises/7", "/topic/courses/42/operation-progress", "/topic/communication/notification/42",
            "/user/topic/notification/all", "/user/topic/notification/42" })
    void testServerOwnedFeedsRejectClientMessages(String destination) {
        var admin = new UsernamePasswordAuthenticationToken("admin", "irrelevant", List.of(new SimpleGrantedAuthority(Role.ADMIN.getAuthority())));
        assertThat(isGranted(admin, StompCommand.SEND, destination)).isFalse();
        assertThat(isGranted(admin, StompCommand.MESSAGE, destination)).isFalse();
    }

    /**
     * Every destination the client publishes to maps to a {@code @MessageMapping} handler under the application prefix.
     */
    @ParameterizedTest
    @ValueSource(strings = { "/app/iris/command-ack", "/app/exercises/42/synchronization", "/app/participations/1/team/trigger", "/app/participations/1/team/typing",
            "/app/participations/1/team/modeling-submissions/update", "/app/participations/1/team/modeling-submissions/patch", "/app/participations/1/team/text-submissions/update",
            "/app/participations/1/team/text-submissions/patch" })
    void testLegitimateClientMessagesAreAllowed(String destination) {
        var user = new UsernamePasswordAuthenticationToken("student", "irrelevant", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        assertThat(isGranted(user, StompCommand.SEND, destination)).isTrue();
    }

    /**
     * Pattern, broker-internal, resolved per-user and malformed course destinations never open a subscription for a user outside every course.
     */
    @ParameterizedTest
    @ValueSource(strings = { " ", "/topic/**", "/topic/*", "/topic/#", "/topic/>", "/topic/{courseId}", "/topic/a,/topic/b", "/topic/a?option=true", "/topic/atlas/orchestrator/*",
            "/topic/atlas/orchestrator/#", "/topic/notification/42-uservictim", "/topic/iris/competencies/42-uservictim", "/topic/communication/notification/42",
            "/topic/unresolved-user/child", "/topic/user-registry/child", "/topic/atlas/orchestrator/", "/topic/atlas/orchestrator", "/topic/atlas/orchestrator/42/extra",
            "/topic/atlas/orchestrator/missing", "/topic/atlas/orchestrator/-1", "/topic/atlas/orchestrator/9223372036854775808", "/topic/courses",
            "/topic/courses/missing/quizExercises/1", "/topic/iris/command-ack" })
    void testUnsafeSubscriptionsAreRejected(String destination) {
        assertThat(registry.authorizeSubscription(authentication("outsider2", Role.STUDENT), destination)).isNotEqualTo(ALLOWED);
    }

    @Test
    void testOrchestrationSummariesStayWithinTheCourse() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider1");
        Course ownCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Course otherCourse = courseUtilService.addEmptyCourse();
        String ownTopic = "/topic/atlas/orchestrator/" + ownCourse.getId();
        String otherTopic = "/topic/atlas/orchestrator/" + otherCourse.getId();

        assertThat(registry.authorizeSubscription(authentication("instructor1", Role.INSTRUCTOR), ownTopic)).isEqualTo(ALLOWED);
        assertThat(registry.authorizeSubscription(authentication("tutor1", Role.TEACHING_ASSISTANT), ownTopic)).isEqualTo(ALLOWED);
        assertThat(registry.authorizeSubscription(authentication("instructor1", Role.INSTRUCTOR), otherTopic)).as("staff of another course").isEqualTo(DENIED);
        assertThat(registry.authorizeSubscription(authentication("student1", Role.STUDENT), ownTopic)).as("student of the course").isEqualTo(DENIED);
        assertThat(registry.authorizeSubscription(authentication("outsider1", Role.STUDENT), ownTopic)).as("user outside the course").isEqualTo(DENIED);
    }

    private static Authentication authentication(String loginWithoutPrefix, Role role) {
        return new UsernamePasswordAuthenticationToken(TEST_PREFIX + loginWithoutPrefix, "irrelevant", List.of(new SimpleGrantedAuthority(role.getAuthority())));
    }
}
