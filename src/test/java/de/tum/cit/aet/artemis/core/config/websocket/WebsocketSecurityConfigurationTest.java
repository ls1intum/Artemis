package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

class WebsocketSecurityConfigurationTest {

    private AuthorizationManager<Message<?>> manager;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ElevatedAccessService> provider = mock(ObjectProvider.class);
        ElevatedAccessService elevation = mock(ElevatedAccessService.class);
        when(provider.getObject()).thenReturn(elevation);
        when(elevation.isAdminElevationActive(any()))
                .thenAnswer(invocation -> invocation.<org.springframework.security.core.Authentication>getArgument(0).getName().equals("admin"));
        when(courses.existsById(anyLong())).thenAnswer(invocation -> invocation.<Long>getArgument(0) == 42L || invocation.<Long>getArgument(0) == 43L);
        when(users.existsByLoginInCourseWithMinRole(anyString(), anyLong(), any())).thenAnswer(invocation -> {
            String login = invocation.getArgument(0);
            long courseId = invocation.getArgument(1);
            Collection<CourseRole> roles = invocation.getArgument(2);
            return switch (login) {
                case "course-a" -> courseId == 42L && roles.contains(CourseRole.INSTRUCTOR);
                case "course-b" -> courseId == 43L && roles.contains(CourseRole.INSTRUCTOR);
                case "student" -> courseId == 42L && roles.contains(CourseRole.STUDENT);
                case "editor" -> courseId == 42L && roles.contains(CourseRole.EDITOR);
                default -> false;
            };
        });
        manager = new WebsocketSecurityConfiguration().authorizationManager(new MessageMatcherDelegatingAuthorizationManager.Builder(), users, courses, provider);
    }

    @Test
    void wildcardSubscriptionCannotReceiveTwoCourses() {
        var registry = new DefaultSubscriptionRegistry();
        register(registry, "course-a", "/topic/atlas/orchestrator/42");
        register(registry, "course-b", "/topic/atlas/orchestrator/43");
        register(registry, "outsider", "/topic/**");

        assertThat(registry.findSubscriptions(message(SimpMessageType.MESSAGE, "/topic/atlas/orchestrator/42", "server")).keySet()).containsExactly("course-a");
        assertThat(registry.findSubscriptions(message(SimpMessageType.MESSAGE, "/topic/atlas/orchestrator/43", "server")).keySet()).containsExactly("course-b");
    }

    @Test
    void clientCannotSpoofNotification() {
        assertThat(allowed(message(SimpMessageType.MESSAGE, "/topic/notification/system-notification", "outsider"))).isFalse();
    }

    @Test
    void clientCannotReadRawPersonalNotification() {
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/topic/notification/42-uservictim", "outsider"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/topic/atlas/orchestrator/", "/topic/atlas/orchestrator", "/topic/atlas/orchestrator/42/", "/topic/atlas/orchestrator/42/extra",
            "/topic/atlas/orchestrator/0", "/topic/atlas/orchestrator/-1", "/topic/atlas/orchestrator/9223372036854775808", "/topic/atlas/orchestrator/missing", "/topic/courses",
            "/topic/courses/missing/quizExercises/1", "/user/topic/notification/missing", "/user/topic/iris/competencies", "/user/topic/iris/competencies/42/extra" })
    void malformedCourseDestinationsCannotFallThrough(String destination) {
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, destination, "admin"))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "/topic/**", "/topic/*", "/topic/#", "/topic/>", "/topic/{courseId}", "/topic/a,/topic/b", "/topic/a?option=true",
            "/topic/notification/42-uservictim", "/topic/iris/competencies/42-uservictim", "/topic/communication/notification/42", "/topic/unresolved-user",
            "/topic/unresolved-user/child", "/topic/user-registry", "/topic/user-registry/child" })
    void unsafeSubscriptionsDeniedEvenForAdministrator(String destination) {
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, destination, "admin"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/topic/atlas/orchestrator/42", "/topic/courses/42/operation-progress", "/topic/courses/42/export-course", "/topic/courses/42/queued-jobs",
            "/topic/courses/42/running-jobs", "/topic/courses/42/finished-jobs", "/topic/courses/42/build-job/1" })
    void instructorFeedsRequireMembershipInThatCourse(String destination) {
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, destination, "course-a"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, destination.replace("/42", "/43"), "course-a"))).isFalse();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, destination, "student"))).isFalse();
    }

    @Test
    void courseAndPersonalFeedsRemainAvailableAtTheirRequiredRoles() {
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/topic/courses/42/quizExercises/7", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/topic/courses/43/quizExercises/7", "student"))).isFalse();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/notification/42", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/notification/43", "student"))).isFalse();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/notification/all", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/iris/competencies/42", "editor"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/iris/competencies/42", "student"))).isFalse();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/user/topic/iris/session/7", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.SUBSCRIBE, "/topic/notification/system-notification", "student"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/topic/atlas/orchestrator/42", "/topic/courses/42/quizExercises/7", "/topic/notification/system-notification", "/topic/communication/notification/42",
            "/topic/iris/session/7", "/user/topic/notification/all", "/user/topic/iris/competencies/42" })
    void serverOwnedFeedsRejectClientMessages(String destination) {
        assertThat(allowed(message(SimpMessageType.MESSAGE, destination, "admin"))).isFalse();
    }

    @Test
    void controlFramesAndUnrelatedApplicationMessagesKeepTheirAuthorization() {
        assertThat(allowed(message(SimpMessageType.CONNECT, null, "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.DISCONNECT, null, "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.UNSUBSCRIBE, null, "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.MESSAGE, "/topic/participations/1/team", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.MESSAGE, "/topic/participations/1/files/sync", "student"))).isTrue();
        assertThat(allowed(message(SimpMessageType.MESSAGE, null, "student"))).isFalse();
        var anonymous = new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(manager.authorize(() -> anonymous, message(SimpMessageType.SUBSCRIBE, "/user/topic/notification/all", "anonymous")).isGranted()).isFalse();
    }

    private void register(DefaultSubscriptionRegistry registry, String session, String destination) {
        Message<?> subscription = message(SimpMessageType.SUBSCRIBE, destination, session);
        if (allowed(subscription)) {
            registry.registerSubscription(subscription);
        }
    }

    private boolean allowed(Message<?> message) {
        return manager.authorize(() -> new TestingAuthenticationToken((String) message.getHeaders().get("simpSessionId"), "password", "ROLE_USER"), message).isGranted();
    }

    private static Message<?> message(SimpMessageType type, String destination, String session) {
        var headers = SimpMessageHeaderAccessor.create(type);
        headers.setDestination(destination);
        headers.setSessionId(session);
        headers.setSubscriptionId("subscription");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
