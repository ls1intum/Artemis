package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class AuthorizationCheckServiceAdminElevationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCourseRoleRepository userCourseRoleRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AdminAccessService adminAccessService;

    private AuthorizationCheckService authorizationCheckService;

    private User admin;

    private Course course;

    @BeforeEach
    void setUp() {
        authorizationCheckService = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository, adminAccessService);
        admin = new User();
        admin.setId(1L);
        admin.setLogin("admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        course = new Course();
        course.setId(2L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "password", Set.of(new SimpleGrantedAuthority(Role.ADMIN.getAuthority()))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDenyCurrentAdminWithoutCourseRoleOrElevation() {
        when(adminAccessService.isAdminElevationActive()).thenReturn(false);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isFalse();
    }

    @Test
    void shouldPreserveCurrentAdminsExplicitCourseRoleWithoutElevation() {
        admin.setCourseRoles(Set.of(new UserCourseRole(admin, course, CourseRole.INSTRUCTOR)));

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository, adminAccessService);
    }

    @Test
    void shouldAllowCurrentAdminWithoutCourseRoleWithElevation() {
        when(adminAccessService.isAdminElevationActive()).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
    }

    @Test
    void shouldPreserveArbitraryAdminAccountClassification() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("different-user", "password", Set.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository, adminAccessService);
    }

    @Test
    void shouldApplyElevationToResourceIdChecks() {
        when(userRepository.isAtLeastInstructorInCourse("admin", course.getId())).thenReturn(false);
        when(adminAccessService.isAdminElevationActive()).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course.getId())).isTrue();
    }
}
