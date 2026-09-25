package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@SuppressWarnings("unchecked")
class TopicSubscriptionInterceptorTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "topicsubscriptioninterceptor";

    @Autowired
    private WebsocketConfiguration websocketConfiguration;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    /**
     * The admin build queue, job and agent topics need request-bound elevation, not the persisted administrator role:
     * the session the handshake established has to carry the administrator authority, and the account has to still be
     * an administrator. The passkey half of that decision is configuration-dependent and is covered by
     * {@code ElevatedAccessServiceTest}; this asserts that the destination check goes through elevation at all.
     */
    @Test
    void testAdministratorSubscriptionRequiresElevationRatherThanAccountClassification() {
        userUtilService.addAdmin(TEST_PREFIX);
        String adminLogin = TEST_PREFIX + "admin";

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var msgMock = (Message<String>) mock(Message.class);
        try (var ignored = mockStatic(StompHeaderAccessor.class)) {
            var headerAccessorMock = mock(StompHeaderAccessor.class);
            when(StompHeaderAccessor.wrap(msgMock)).thenReturn(headerAccessorMock);
            when(headerAccessorMock.getCommand()).thenReturn(StompCommand.SUBSCRIBE);
            var channel = mock(MessageChannel.class);

            for (String destination : List.of("/topic/admin/queued-jobs", "/topic/admin/running-jobs", "/topic/admin/finished-jobs", "/topic/admin/build-agents")) {
                when(headerAccessorMock.getDestination()).thenReturn(destination);

                // An elevated administrator: the session carries the administrator authority.
                when(headerAccessorMock.getUser()).thenReturn(authenticationFor(adminLogin, Role.ADMIN));
                assertThat(interceptor.preSend(msgMock, channel)).as("an elevated administrator may subscribe to %s", destination).isEqualTo(msgMock);

                // The same account on a session that does not carry the administrator authority.
                when(headerAccessorMock.getUser()).thenReturn(authenticationFor(adminLogin, Role.STUDENT));
                assertThat(interceptor.preSend(msgMock, channel)).as("a session without the administrator authority must not subscribe to %s", destination).isNull();

                // A principal that is not an Authentication carries no authorities to check at all.
                var principalMock = mock(Principal.class);
                when(principalMock.getName()).thenReturn(adminLogin);
                when(headerAccessorMock.getUser()).thenReturn(principalMock);
                assertThat(interceptor.preSend(msgMock, channel)).as("a principal without authorities must not subscribe to %s", destination).isNull();
            }
        }
    }

    private static Authentication authenticationFor(String login, Role role) {
        return new UsernamePasswordAuthenticationToken(login, "irrelevant", List.of(new SimpleGrantedAuthority(role.getAuthority())));
    }

    @Test
    void testRoleProtectedSubscriptionsUseWebsocketAuthentication() {
        userUtilService.addAdmin(TEST_PREFIX);
        String adminLogin = TEST_PREFIX + "admin";
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        var exercise = course.getExercises().stream().findFirst().orElseThrow();
        var participation = exercise.getStudentParticipations().stream().findFirst().orElseThrow();
        var exam = examUtilService.addExerciseGroupsAndExercisesToExam(examUtilService.addExam(course), false);
        var examExercise = exam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();
        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var channel = mock(MessageChannel.class);
        var previousContext = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            for (String destination : List.of("/topic/courses/" + course.getId() + "/queued-jobs", "/topic/courses/" + course.getId() + "/running-jobs",
                    "/topic/courses/" + course.getId() + "/build-job/test-job", "/topic/exercise/" + exercise.getId() + "/newResults",
                    "/topic/exercise/" + examExercise.getId() + "/newResults", "/topic/exams/" + exam.getId() + "/exercise-start-status",
                    "/topic/exercises/" + exercise.getId() + "/synchronization")) {
                SecurityContextHolder.clearContext();
                var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
                headers.setLeaveMutable(true);
                headers.setDestination(destination);
                headers.setUser(authenticationFor(adminLogin, Role.ADMIN));
                var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
                assertThat(interceptor.preSend(message, channel)).as("elevated administrator: %s", destination).isSameAs(message);
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

                // Even an elevated thread context must not override the WebSocket session's permissions.
                SecurityContextHolder.getContext().setAuthentication(authenticationFor(adminLogin, Role.ADMIN));
                headers.setUser(authenticationFor(adminLogin, Role.STUDENT));
                message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
                assertThat(interceptor.preSend(message, channel)).as("non-elevated session: %s", destination).isNull();

                headers.setUser(() -> adminLogin);
                message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
                assertThat(interceptor.preSend(message, channel)).as("principal without authentication: %s", destination).isNull();

                headers.setUser(authenticationFor(TEST_PREFIX + "instructor1", Role.INSTRUCTOR));
                message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
                assertThat(interceptor.preSend(message, channel)).as("explicit instructor: %s", destination).isSameAs(message);
            }

            var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            headers.setDestination("/topic/participations/" + participation.getId() + "/team");
            headers.setUser(authenticationFor(adminLogin, Role.ADMIN));
            var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
            assertThat(interceptor.preSend(message, channel)).as("administrator override must not bypass ownership").isNull();
        }
        finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    @Test
    void testUserTopicSubscriptionIsLimitedToTheUserItNames() {
        userUtilService.addAdmin(TEST_PREFIX);
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        long student1Id = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
        long student2Id = userUtilService.getUserByLogin(TEST_PREFIX + "student2").getId();
        String student1Topic = "/topic/user/" + student1Id + "/notifications/conversations";

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var channel = mock(MessageChannel.class);

        var message = subscribeMessage(student1Topic, authenticationFor(TEST_PREFIX + "student1", Role.STUDENT));
        assertThat(interceptor.preSend(message, channel)).as("the user the topic names").isSameAs(message);

        message = subscribeMessage("/topic/user/" + student2Id + "/notifications/conversations", authenticationFor(TEST_PREFIX + "student1", Role.STUDENT));
        assertThat(interceptor.preSend(message, channel)).as("another user's topic").isNull();

        message = subscribeMessage(student1Topic, authenticationFor(TEST_PREFIX + "admin", Role.ADMIN));
        assertThat(interceptor.preSend(message, channel)).as("administrator on another user's topic").isNull();

        for (String destination : List.of("/topic/user/0" + student1Id + "/notifications/conversations", "/topic/user/" + student1Id, "/topic/user/" + student1Id + "/",
                "/topic/user/login/notifications/conversations", "/topic/user/99999999999999999999/notifications/conversations")) {
            message = subscribeMessage(destination, authenticationFor(TEST_PREFIX + "student1", Role.STUDENT));
            assertThat(interceptor.preSend(message, channel)).as("malformed user topic %s", destination).isNull();
        }
    }

    @Test
    void testPatternSubscriptionIsRejected() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        long student1Id = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var channel = mock(MessageChannel.class);
        var student = authenticationFor(TEST_PREFIX + "student1", Role.STUDENT);

        var message = subscribeMessage("/topic/management/feature-toggles", student);
        assertThat(interceptor.preSend(message, channel)).as("a topic without a pattern").isSameAs(message);

        for (String destination : List.of("/topic/**", "/topic/*/" + student1Id + "/notifications/conversations", "/topic/user/" + student1Id + "/**",
                "/topic/user/{userId}/notifications/conversations", "/topic/user/" + student1Id + "/notifications/conversation?", "/topic/#", "/topic/>", "/user/topic/**")) {
            message = subscribeMessage(destination, student);
            assertThat(interceptor.preSend(message, channel)).as("pattern subscription %s", destination).isNull();
        }

        message = subscribeMessage(null, student);
        assertThat(interceptor.preSend(message, channel)).as("subscription without destination").isNull();
    }

    private static Message<byte[]> subscribeMessage(String destination, Principal user) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setUser(user);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void testAllowSubscription() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        var exercise = course.getExercises().stream().findFirst().orElseThrow();
        var participation = exercise.getStudentParticipations().stream().findFirst().orElseThrow();

        var exam = examUtilService.addExam(course);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        var examExercise = exam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var msgMock = (Message<String>) mock(Message.class);
        try (var ignored = mockStatic(StompHeaderAccessor.class)) {
            var headerAccessorMock = mock(StompHeaderAccessor.class);
            when(StompHeaderAccessor.wrap(msgMock)).thenReturn(headerAccessorMock);
            when(headerAccessorMock.getCommand()).thenReturn(StompCommand.SUBSCRIBE);
            var principalMock = mock(Principal.class);
            when(headerAccessorMock.getUser()).thenReturn(principalMock);

            var channel = mock(MessageChannel.class);

            // Team Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/participations/" + participation.getId() + "/team");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            var returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student2");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Non Personal Exercise Result Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + exercise.getId() + "/newResults");

            // Normal course exercise
            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exam exercise
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + examExercise.getId() + "/newResults");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exam destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exams/" + exam.getId() + "/test");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            // Exercise synchronization destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercises/" + exercise.getId() + "/synchronization");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isEqualTo(msgMock);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "tutor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertThat(returnedValue).isNull();
        }
    }
}
