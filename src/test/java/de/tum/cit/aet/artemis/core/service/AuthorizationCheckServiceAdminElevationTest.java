package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.security.Role;
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
        authorizationCheckService = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository);
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
    void shouldRejectStaleAdministratorAuthorityAfterRoleWasRevoked() {
        authenticate("admin", Role.ADMIN);
        when(userRepository.isAdmin("admin")).thenReturn(false);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isFalse();
        verify(userRepository).isAdmin("admin");
    }

    private void authenticate(String login, Role... roles) {
        var authorities = Set.of(roles).stream().map(role -> new SimpleGrantedAuthority(role.getAuthority())).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", authorities));
    }
}
