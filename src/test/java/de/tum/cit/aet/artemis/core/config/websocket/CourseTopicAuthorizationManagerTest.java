package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;

@ExtendWith(MockitoExtension.class)
class CourseTopicAuthorizationManagerTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private ObjectProvider<ElevatedAccessService> elevationProvider;

    @Mock
    private ElevatedAccessService elevation;

    private CourseTopicAuthorizationManager manager;

    @BeforeEach
    void setUp() {
        manager = new CourseTopicAuthorizationManager(userRepository, courseRepository, elevationProvider, CourseRole.INSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(value = CourseRole.class, names = { "STUDENT", "EDITOR", "INSTRUCTOR" })
    void requiresCourseSpecificMinimumRole(CourseRole minimum) {
        manager = new CourseTopicAuthorizationManager(userRepository, courseRepository, elevationProvider, minimum);
        when(elevationProvider.getObject()).thenReturn(elevation);
        when(courseRepository.existsById(42L)).thenReturn(true);
        when(userRepository.existsByLoginInCourseWithMinRole(anyString(), anyLong(), any())).thenAnswer(invocation -> {
            CourseRole actual = CourseRole.valueOf(invocation.getArgument(0));
            Collection<CourseRole> roles = invocation.getArgument(2);
            return invocation.<Long>getArgument(1) == 42L && roles.contains(actual);
        });
        for (CourseRole actual : CourseRole.values()) {
            assertThat(authorize(authenticated(actual.name()), "42")).isEqualTo(CourseRole.valuesAtLeast(minimum).contains(actual));
            assertThat(authorize(authenticated(actual.name()), "43")).isFalse();
        }
    }

    @Test
    void elevatedAdministratorStillNeedsExistingCourse() {
        Authentication admin = authenticated("admin");
        when(elevationProvider.getObject()).thenReturn(elevation);
        when(elevation.isAdminElevationActive(admin)).thenReturn(true);
        when(courseRepository.existsById(42L)).thenReturn(true);
        assertThat(authorize(admin, "42")).isTrue();
        assertThat(authorize(admin, "43")).isFalse();
    }

    @Test
    void elevationUsesMessagePrincipalInsteadOfAmbientAdministrator() {
        Authentication ambient = authenticated("ambient-admin");
        Authentication message = authenticated("password-only-admin");
        when(elevationProvider.getObject()).thenReturn(elevation);
        when(elevation.isAdminElevationActive(any())).thenAnswer(invocation -> invocation.getArgument(0) == ambient);
        SecurityContextHolder.getContext().setAuthentication(ambient);
        try {
            assertThat(authorize(message, "42")).isFalse();
            verifyNoInteractions(courseRepository);
        }
        finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "invalid", "0", "-1", "9223372036854775808" })
    void invalidCourseIdDeniesWithoutLookup(String courseId) {
        assertThat(authorize(authenticated("instructor"), courseId)).isFalse();
        verifyNoInteractions(userRepository, courseRepository, elevationProvider, elevation);
    }

    @Test
    void invalidPrincipalsDenyWithoutLookup() {
        assertThat(authorize(null, "42")).isFalse();
        assertThat(authorize(new TestingAuthenticationToken("instructor", "password"), "42")).isFalse();
        assertThat(authorize(new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")), "42")).isFalse();
        assertThat(authorize(authenticated(" "), "42")).isFalse();
        Authentication unnamed = mock(Authentication.class);
        when(unnamed.isAuthenticated()).thenReturn(true);
        assertThat(authorize(unnamed, "42")).isFalse();
        verifyNoInteractions(userRepository, courseRepository, elevationProvider, elevation);
    }

    @Test
    void membershipFailureDenies() {
        when(userRepository.existsByLoginInCourseWithMinRole(anyString(), anyLong(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(authorize(authenticated("instructor"), "42")).isFalse();
    }

    @Test
    void existenceFailureDenies() {
        when(userRepository.existsByLoginInCourseWithMinRole(anyString(), anyLong(), any())).thenReturn(true);
        when(courseRepository.existsById(42L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(authorize(authenticated("instructor"), "42")).isFalse();
    }

    @Test
    void elevationFailureDenies() {
        when(elevationProvider.getObject()).thenReturn(elevation);
        when(elevation.isAdminElevationActive(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(authorize(authenticated("admin"), "42")).isFalse();
    }

    private boolean authorize(Authentication authentication, String courseId) {
        Map<String, String> variables = courseId == null ? Map.of() : Map.of("courseId", courseId);
        return manager.authorize(() -> authentication, new MessageAuthorizationContext<>(new GenericMessage<>(new byte[0]), variables)).isGranted();
    }

    private static Authentication authenticated(String login) {
        return new TestingAuthenticationToken(login, "password", "ROLE_USER");
    }
}
