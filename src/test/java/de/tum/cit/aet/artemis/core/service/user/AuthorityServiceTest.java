package de.tum.cit.aet.artemis.core.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class AuthorityServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "authorityservice";

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

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
        Set<Authority> initialAuthorities = new HashSet<>();
        initialAuthorities.add(Authority.SUPER_ADMIN_AUTHORITY);
        user.setAuthorities(initialAuthorities);
        user.setGroups(new HashSet<>());

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
        Set<Authority> initialAuthorities = new HashSet<>();
        initialAuthorities.add(Authority.ADMIN_AUTHORITY);
        user.setAuthorities(initialAuthorities);
        user.setGroups(new HashSet<>());

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
        Set<Authority> initialAuthorities = new HashSet<>();
        initialAuthorities.add(Authority.SUPER_ADMIN_AUTHORITY);
        initialAuthorities.add(Authority.ADMIN_AUTHORITY);
        user.setAuthorities(initialAuthorities);
        user.setGroups(new HashSet<>());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify both authorities are preserved
        assertThat(authorities).contains(Authority.SUPER_ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.ADMIN_AUTHORITY);
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddInstructorAuthorityBasedOnGroup() {
        // Create a user with instructor group
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        Set<String> groups = new HashSet<>();
        groups.add(course.getInstructorGroupName());
        user.setGroups(groups);
        user.setAuthorities(new HashSet<>());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify INSTRUCTOR authority is added based on group
        assertThat(authorities).contains(new Authority(Role.INSTRUCTOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddEditorAuthorityBasedOnGroup() {
        // Create a user with editor group
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<String> groups = new HashSet<>();
        groups.add(course.getEditorGroupName());
        user.setGroups(groups);
        user.setAuthorities(new HashSet<>());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify EDITOR authority is added based on group
        assertThat(authorities).contains(new Authority(Role.EDITOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldAddTeachingAssistantAuthorityBasedOnGroup() {
        // Create a user with teaching assistant group
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<String> groups = new HashSet<>();
        groups.add(course.getTeachingAssistantGroupName());
        user.setGroups(groups);
        user.setAuthorities(new HashSet<>());

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify TEACHING_ASSISTANT authority is added based on group
        assertThat(authorities).contains(new Authority(Role.TEACHING_ASSISTANT.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }

    @Test
    void testBuildAuthorities_shouldHandleNullGroups() {
        // Create a user with null groups
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setGroups(null);
        user.setAuthorities(new HashSet<>());

        // Call buildAuthorities - should not throw exception
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify at least STUDENT authority is added
        assertThat(authorities).contains(Authority.USER_AUTHORITY);
    }

    @Test
    void testBuildAuthorities_shouldCombinePreservedAndGroupBasedAuthorities() {
        // Create a super admin user with instructor group
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        Set<Authority> initialAuthorities = new HashSet<>();
        initialAuthorities.add(Authority.SUPER_ADMIN_AUTHORITY);
        user.setAuthorities(initialAuthorities);
        Set<String> groups = new HashSet<>();
        groups.add(course.getInstructorGroupName());
        user.setGroups(groups);

        // Call buildAuthorities
        Set<Authority> authorities = authorityService.buildAuthorities(user);

        // Verify both preserved and group-based authorities are present
        assertThat(authorities).contains(Authority.SUPER_ADMIN_AUTHORITY);
        assertThat(authorities).contains(new Authority(Role.INSTRUCTOR.getAuthority()));
        assertThat(authorities).contains(Authority.USER_AUTHORITY); // STUDENT role is always added
    }
}
