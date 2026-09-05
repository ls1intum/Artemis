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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
