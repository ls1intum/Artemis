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
import org.springframework.beans.factory.ObjectProvider;
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

    @Mock
    private ElevatedAccessService elevatedAccessService;

    @Mock
    private ObjectProvider<ElevatedAccessService> elevatedAccessServiceProvider;

    private AuthorizationCheckService authorizationCheckService;

    private User admin;

    private Course course;

    @BeforeEach
    void setUp() {
        authorizationCheckService = new AuthorizationCheckService(userRepository, userCourseRoleRepository, teamRepository, elevatedAccessServiceProvider);
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
        when(elevatedAccessServiceProvider.getObject()).thenReturn(elevatedAccessService);
        when(elevatedAccessService.isAdminElevationActive()).thenReturn(false);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isFalse();
    }

    @Test
    void shouldPreserveCurrentAdminsExplicitCourseRoleWithoutElevation() {
        admin.setCourseRoles(Set.of(new UserCourseRole(admin, course, CourseRole.INSTRUCTOR)));

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository, elevatedAccessServiceProvider, elevatedAccessService);
    }

    @Test
    void shouldAllowCurrentAdminWithoutCourseRoleWithElevation() {
        when(elevatedAccessServiceProvider.getObject()).thenReturn(elevatedAccessService);
        when(elevatedAccessService.isAdminElevationActive()).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
    }

    @Test
    void shouldPreserveArbitraryAdminAccountClassification() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("different-user", "password", Set.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course, admin)).isTrue();
        verifyNoInteractions(userCourseRoleRepository, elevatedAccessServiceProvider, elevatedAccessService);
    }

    @Test
    void shouldApplyElevationToResourceIdChecks() {
        when(userRepository.isAtLeastInstructorInCourse("admin", course.getId())).thenReturn(false);
        when(elevatedAccessServiceProvider.getObject()).thenReturn(elevatedAccessService);
        when(elevatedAccessService.isAdminElevationActive()).thenReturn(true);

        assertThat(authorizationCheckService.isAtLeastInstructorInCourse(course.getId())).isTrue();
    }
}
