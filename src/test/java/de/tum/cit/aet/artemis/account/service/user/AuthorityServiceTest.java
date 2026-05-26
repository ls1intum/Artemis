package de.tum.cit.aet.artemis.account.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class AuthorityServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "authorityservice";

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
    }

    @Test
    void testBuildAuthorities_shouldPreserveSuperAdminAuthority() {
        // Create a user with SUPER_ADMIN authority
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));
        user.setGroups(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify SUPER_ADMIN authority is preserved
        assertThat(authorities).contains(Authority.SUPER_ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldPreserveAdminAuthority() {
        // Create a user with ADMIN authority
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        user.setGroups(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify ADMIN authority is preserved
        assertThat(authorities).contains(Authority.ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldPreserveBothAdminAndSuperAdminAuthorities() {
        // Create a user with both ADMIN and SUPER_ADMIN authorities
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY, Authority.ADMIN_AUTHORITY));
        user.setGroups(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify both authorities are preserved
        assertThat(authorities).contains(Authority.SUPER_ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddInstructorAuthorityBasedOnCourseRole() {
        // Enroll user as instructor in the course (the new UCR-based mechanism)
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userCourseRoleRepository.save(new UserCourseRole(user, course, CourseRole.INSTRUCTOR));
        user.setAuthorities(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify INSTRUCTOR authority is granted based on user_course_role
        assertThat(authorities).contains(new Authority(Role.INSTRUCTOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddEditorAuthorityBasedOnCourseRole() {
        // Enroll user as editor in the course (the new UCR-based mechanism)
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userCourseRoleRepository.save(new UserCourseRole(user, course, CourseRole.EDITOR));
        user.setAuthorities(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify EDITOR authority is granted based on user_course_role
        assertThat(authorities).contains(new Authority(Role.EDITOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddTeachingAssistantAuthorityBasedOnCourseRole() {
        // Enroll user as teaching assistant in the course (the new UCR-based mechanism)
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userCourseRoleRepository.save(new UserCourseRole(user, course, CourseRole.TEACHING_ASSISTANT));
        user.setAuthorities(Set.of());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify TEACHING_ASSISTANT authority is granted based on user_course_role
        assertThat(authorities).contains(new Authority(Role.TEACHING_ASSISTANT.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldOnlyGrantStudentAuthorityWhenNoEnrollment() {
        // A user with no UCR entries should only get STUDENT authority
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setAuthorities(Set.of());

        // Call buildAuthorities - should not throw exception
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify at least STUDENT authority is added (and no higher roles)
        assertThat(authorities).contains(Authority.USER_AUTHORITY);
        assertThat(authorities).doesNotContain(new Authority(Role.INSTRUCTOR.getAuthority()));
        assertThat(authorities).doesNotContain(new Authority(Role.EDITOR.getAuthority()));
        assertThat(authorities).doesNotContain(new Authority(Role.TEACHING_ASSISTANT.getAuthority()));
    }

    @Test
    void testBuildAuthorities_shouldCombinePreservedAndCourseRoleBasedAuthorities() {
        // A super admin user who is also an instructor in a course should have both authorities
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userCourseRoleRepository.save(new UserCourseRole(user, course, CourseRole.INSTRUCTOR));
        user.setAuthorities(Set.of(Authority.SUPER_ADMIN_AUTHORITY));

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify both preserved and UCR-based authorities are present
        assertThat(authorities).contains(Authority.SUPER_ADMIN_AUTHORITY);
        assertThat(authorities).contains(new Authority(Role.INSTRUCTOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }
}
