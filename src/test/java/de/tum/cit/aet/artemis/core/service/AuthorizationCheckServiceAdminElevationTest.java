package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class AuthorizationCheckServiceAdminElevationTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private UserCourseRoleTestRepository userCourseRoleRepository;

    @Mock
    private TeamRepository teamRepository;

    private AuthorizationCheckService authorizationCheckService;

    private User admin;

    private Course course;

    @BeforeEach
    void setUp() {
        authorizationCheckService = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository, false);
        admin = new User();
        admin.setId(1L);
        admin.setLogin("admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        course = new Course();
        course.setId(2L);
        authenticate("admin");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldDenyCurrentAdminWithoutCourseRoleOrElevation() {
        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isFalse();
        verify(userRepository, never()).isAdmin("admin");
    }

    @Test
    void shouldPreserveCurrentAdminsExplicitCourseRoleWithoutElevation() {
        admin.setCourseRoles(Set.of(new UserCourseRole(admin, course, CourseRole.INSTRUCTOR)));

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository);
        verify(userRepository, never()).isAdmin("admin");
    }

    @Test
    void shouldAllowCurrentAdminWithoutCourseRoleWithElevation() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verify(userRepository).isAdmin("admin");
    }

    @Test
    void shouldPreserveArbitraryAdminAccountClassification() {
        authenticate("different-user", Role.STUDENT);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository);
        verify(userRepository, never()).isAdmin("different-user");
    }

    @Test
    void shouldApplyElevationToResourceIdChecks() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAtLeastInstructorInCourse("admin", course.getId())).thenReturn(false);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course.getId())).isTrue();
        verify(userRepository).isAdmin("admin");
    }

    @Test
    void shouldApplyElevationToAllCurrentLoginChecks() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastEditorInCourse("admin", course.getId())).isTrue();
        assertThat(authorizationCheckService.isAtLeastInstructorInCourse("admin", course.getId())).isTrue();
        assertThat(authorizationCheckService.isAtLeastTeachingAssistantInCourse("admin", course.getId())).isTrue();
        assertThat(authorizationCheckService.isAtLeastTeachingAssistantInExercise("admin", 3L)).isTrue();
        assertThat(authorizationCheckService.isAtLeastEditorInExercise("admin", 3L)).isTrue();
        assertThat(authorizationCheckService.isAtLeastInstructorInExercise("admin", 3L)).isTrue();
        verify(userRepository, times(6)).isAdmin("admin");
    }

    /**
     * A course or exercise list asks for the administrator override once per entry, so the persisted lookup has to
     * happen once per request rather than once per check.
     */
    @Test
    void shouldResolvePersistedAdministratorStatusOncePerRequest() {
        authenticate("admin", Role.ADMIN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course.getId())).isTrue();
        assertThat(authorizationCheckService.isCurrentUserAdminAccessEnabled()).isTrue();
        verify(userRepository, times(1)).isAdmin("admin");
    }

    /**
     * An LTI launch replaces the authentication part way through a request, so a cached answer must not survive the
     * login it was resolved for.
     */
    @Test
    void shouldNotReuseCachedAdministratorStatusForAnotherLogin() {
        authenticate("admin", Role.ADMIN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(userRepository.isAdmin("admin")).thenReturn(true);
        when(userRepository.isAdmin("other-admin")).thenReturn(false);

        assertThat(authorizationCheckService.isCurrentUserAdminAccessEnabled()).isTrue();
        authenticate("other-admin", Role.ADMIN);
        assertThat(authorizationCheckService.isCurrentUserAdminAccessEnabled()).isFalse();
        verify(userRepository).isAdmin("admin");
        verify(userRepository).isAdmin("other-admin");
    }

    /**
     * With the passkey requirement enabled the override is the same decision {@code ElevatedAccessService} makes: the
     * session has to prove an approved passkey, and a password session never reaches the persisted lookup.
     */
    @Test
    void shouldRequireAnApprovedPasskeyWhenTheRequirementIsEnabled() {
        var serviceRequiringPasskey = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository, true);
        authenticate("admin", Role.ADMIN);

        assertThat(serviceRequiringPasskey.isCurrentUserAdminAccessEnabled()).isFalse();
        verify(userRepository, never()).isAdmin("admin");

        authenticateWithApprovedPasskey("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(serviceRequiringPasskey.isCurrentUserAdminAccessEnabled()).isTrue();
    }

    /**
     * The throwing wrapper decides for the current caller when it is handed no user, so it has to weigh elevation like
     * every other current-caller check rather than accept the administrator authority on its own.
     */
    @Test
    void shouldRequireElevationWhenCheckingTheCurrentCallerIsAnAdministrator() {
        var serviceRequiringPasskey = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository, true);
        authenticate("admin", Role.ADMIN);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> serviceRequiringPasskey.checkIsAdminElseThrow(null));

        authenticateWithApprovedPasskey("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThatNoException().isThrownBy(() -> serviceRequiringPasskey.checkIsAdminElseThrow(null));
    }

    @Test
    void shouldNotApplyCurrentUsersElevationToArbitraryLoginChecks() {
        authenticate("admin", Role.ADMIN);

        assertThat(authorizationCheckService.isAtLeastEditorInCourse("other-user", course.getId())).isFalse();
        assertThat(authorizationCheckService.isAtLeastInstructorInCourse("other-user", course.getId())).isFalse();
        assertThat(authorizationCheckService.isAtLeastTeachingAssistantInCourse("other-user", course.getId())).isFalse();
        assertThat(authorizationCheckService.isAtLeastTeachingAssistantInExercise("other-user", 3L)).isFalse();
        assertThat(authorizationCheckService.isAtLeastEditorInExercise("other-user", 3L)).isFalse();
        assertThat(authorizationCheckService.isAtLeastInstructorInExercise("other-user", 3L)).isFalse();
        verify(userRepository, never()).isAdmin("admin");
        verify(userRepository, never()).isAdmin("other-user");
    }

    @Test
    void shouldRejectStaleAdministratorAuthorityAfterRoleWasRevoked() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(false);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isFalse();
        verify(userRepository).isAdmin("admin");
    }

    @Test
    void shouldRejectStaleSuperAdministratorAuthorityAfterDowngrade() {
        authenticate("admin", Role.ADMIN, Role.SUPER_ADMIN);
        when(userRepository.isSuperAdmin("admin")).thenReturn(false);
        when(userRepository.isAdmin("admin")).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastRoleInCourse(Role.SUPER_ADMIN, course.getId())).isFalse();
        assertThat(authorizationCheckService.isAtLeastRoleInCourse(Role.ADMIN, course.getId())).isTrue();
        verify(userRepository).isSuperAdmin("admin");
        verify(userRepository).isAdmin("admin");
    }

    private void authenticate(String login, Role... roles) {
        SecurityContextHolder.getContext().setAuthentication(tokenFor(login, roles));
    }

    private void authenticateWithApprovedPasskey(String login, Role... roles) {
        var authentication = tokenFor(login, roles);
        authentication.setDetails(Map.of(TokenProvider.IS_AUTHENTICATED_WITH_PASSKEY, true, TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, true));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static UsernamePasswordAuthenticationToken tokenFor(String login, Role... roles) {
        var authorities = Set.of(roles).stream().map(role -> new SimpleGrantedAuthority(role.getAuthority())).toList();
        return new UsernamePasswordAuthenticationToken(login, "password", authorities);
    }
}
