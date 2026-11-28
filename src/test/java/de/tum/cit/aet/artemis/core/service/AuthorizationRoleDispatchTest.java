package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;

class AuthorizationRoleDispatchTest {

    private final UserTestRepository users = mock(UserTestRepository.class);

    private final UserCourseRoleTestRepository roles = mock(UserCourseRoleTestRepository.class);

    private final AuthorizationCheckService service = new AuthorizationCheckService(users, roles, mock(TeamRepository.class), false);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void resourceDispatchUsesMembershipAndRejectsGlobalRolesWithoutElevation(Role requested) {
        authenticate(Role.STUDENT);
        allowResourceMembership(requested);
        boolean allowed = requested != Role.ADMIN && requested != Role.SUPER_ADMIN && requested != Role.ANONYMOUS;
        assertThat(service.isAtLeastRoleInCourse(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInExercise(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInLectureUnit(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInLecture(requested, 1)).isEqualTo(allowed);
        if (allowed) {
            assertThatCode(() -> service.checkIsAtLeastRoleInCourseElseThrow(requested, 1)).doesNotThrowAnyException();
            assertThatCode(() -> service.checkIsAtLeastRoleInExerciseElseThrow(requested, 1)).doesNotThrowAnyException();
            assertThatCode(() -> service.checkIsAtLeastRoleInLectureUnitElseThrow(requested, 1)).doesNotThrowAnyException();
            assertThatCode(() -> service.checkIsAtLeastRoleInLectureElseThrow(requested, 1)).doesNotThrowAnyException();
        }
        else {
            assertThatThrownBy(() -> service.checkIsAtLeastRoleInCourseElseThrow(requested, 1)).isInstanceOf(AccessForbiddenException.class);
            assertThatThrownBy(() -> service.checkIsAtLeastRoleInExerciseElseThrow(requested, 1)).isInstanceOf(AccessForbiddenException.class);
            assertThatThrownBy(() -> service.checkIsAtLeastRoleInLectureUnitElseThrow(requested, 1)).isInstanceOf(AccessForbiddenException.class);
            assertThatThrownBy(() -> service.checkIsAtLeastRoleInLectureElseThrow(requested, 1)).isInstanceOf(AccessForbiddenException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void missingMembershipDeniesEveryResourceRole(Role requested) {
        authenticate(Role.STUDENT);
        assertThat(service.isAtLeastRoleInCourse(requested, 1)).isFalse();
        assertThat(service.isAtLeastRoleInExercise(requested, 1)).isFalse();
        assertThat(service.isAtLeastRoleInLectureUnit(requested, 1)).isFalse();
        assertThat(service.isAtLeastRoleInLecture(requested, 1)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void elevatedSuperAdminCanAccessEveryResourceExceptAnonymousRole(Role requested) {
        authenticate(Role.ADMIN, Role.SUPER_ADMIN);
        when(users.isAdmin("caller")).thenReturn(true);
        when(users.isSuperAdmin("caller")).thenReturn(true);
        boolean allowed = requested != Role.ANONYMOUS;
        assertThat(service.isAtLeastRoleInCourse(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInExercise(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInLectureUnit(requested, 1)).isEqualTo(allowed);
        assertThat(service.isAtLeastRoleInLecture(requested, 1)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "ADMIN", "SUPER_ADMIN" })
    void explicitGlobalRoleChecksRequireTheMatchingAccountAuthority(Role role) {
        Course course = new Course();
        course.setId(1L);
        User account = account(Set.of(Authority.USER_AUTHORITY));
        assertThatThrownBy(() -> service.checkHasAtLeastRoleInCourseElseThrow(role, course, account)).isInstanceOf(AccessForbiddenException.class);
        account.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        if (role == Role.ADMIN) {
            assertThatCode(() -> service.checkHasAtLeastRoleInCourseElseThrow(role, course, account)).doesNotThrowAnyException();
        }
        else {
            assertThatThrownBy(() -> service.checkHasAtLeastRoleInCourseElseThrow(role, course, account)).isInstanceOf(AccessForbiddenException.class);
        }
        account.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));
        assertThatCode(() -> service.checkHasAtLeastRoleInCourseElseThrow(role, course, account)).doesNotThrowAnyException();
    }

    @Test
    void explicitCourseRolesAndGlobalAuthorityDoNotMakeAnAdminOnlyAStudent() {
        Course course = new Course();
        course.setId(1L);
        User account = account(Set.of(Authority.ADMIN_AUTHORITY));
        account.setCourseRoles(Set.of(new UserCourseRole(account, course, CourseRole.STUDENT)));
        assertThat(service.isAtLeastEditorInCourse(1, account)).isTrue();
        assertThat(service.isAtLeastTeachingAssistantInCourse(1, account)).isTrue();
        assertThat(service.isAtLeastStudentInCourse(1, account)).isTrue();
        assertThat(service.isOnlyStudentInCourse(1, account)).isFalse();
        assertThat(service.isOnlyStudentInCourse(course, account)).isFalse();
    }

    private User account(Set<Authority> authorities) {
        User user = new User();
        user.setId(1L);
        user.setLogin("subject");
        user.setAuthorities(authorities);
        return user;
    }

    private void authenticate(Role... roles) {
        var authorities = java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority(role.getAuthority())).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller", "unused", authorities));
    }

    private void allowResourceMembership(Role role) {
        switch (role) {
            case INSTRUCTOR -> {
                when(users.isAtLeastInstructorInCourse("caller", 1)).thenReturn(true);
                when(users.isAtLeastInstructorInExercise("caller", 1)).thenReturn(true);
                when(users.isAtLeastInstructorInLectureUnit("caller", 1)).thenReturn(true);
                when(users.isAtLeastInstructorInLecture("caller", 1)).thenReturn(true);
            }
            case EDITOR -> {
                when(users.isAtLeastEditorInCourse("caller", 1)).thenReturn(true);
                when(users.isAtLeastEditorInExercise("caller", 1)).thenReturn(true);
                when(users.isAtLeastEditorInLectureUnit("caller", 1)).thenReturn(true);
                when(users.isAtLeastEditorInLecture("caller", 1)).thenReturn(true);
            }
            case TEACHING_ASSISTANT -> {
                when(users.isAtLeastTeachingAssistantInCourse("caller", 1)).thenReturn(true);
                when(users.isAtLeastTeachingAssistantInExercise("caller", 1)).thenReturn(true);
                when(users.isAtLeastTeachingAssistantInLectureUnit("caller", 1)).thenReturn(true);
                when(users.isAtLeastTeachingAssistantInLecture("caller", 1)).thenReturn(true);
            }
            case STUDENT -> {
                when(users.isAtLeastStudentInCourse("caller", 1)).thenReturn(true);
                when(users.isAtLeastStudentInExercise("caller", 1)).thenReturn(true);
                when(users.isAtLeastStudentInLectureUnit("caller", 1)).thenReturn(true);
                when(users.isAtLeastStudentInLecture("caller", 1)).thenReturn(true);
            }
            default -> {
            }
        }
    }
}
