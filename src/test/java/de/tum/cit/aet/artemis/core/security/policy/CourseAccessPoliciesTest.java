package de.tum.cit.aet.artemis.core.security.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.policy.definitions.CourseAccessPolicies;

class CourseAccessPoliciesTest {

    private AccessPolicy<Course> policy;

    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        CourseAccessPolicies config = new CourseAccessPolicies();
        policy = config.courseVisibilityPolicy();
        policyEngine = new PolicyEngine();
    }

    private static User createUser(Set<String> groups, Set<Authority> authorities) {
        User user = new User();
        user.setGroups(groups);
        user.setAuthorities(authorities);
        return user;
    }

    private static User createUser(Set<String> groups) {
        return createUser(groups, Set.of());
    }

    private static Course createCourse(ZonedDateTime startDate) {
        Course course = new Course();
        course.setStudentGroupName("course1-students");
        course.setTeachingAssistantGroupName("course1-tas");
        course.setEditorGroupName("course1-editors");
        course.setInstructorGroupName("course1-instructors");
        course.setStartDate(startDate);
        return course;
    }

    @Test
    void testStudentEnrolledCourseStartedIsAllowed() {
        User student = createUser(Set.of("course1-students"));
        Course course = createCourse(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, course)).isTrue();
    }

    @Test
    void testStudentEnrolledCourseNotStartedIsDenied() {
        User student = createUser(Set.of("course1-students"));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, course)).isFalse();
    }

    @Test
    void testStudentEnrolledNoStartDateIsAllowed() {
        User student = createUser(Set.of("course1-students"));
        Course course = createCourse(null);

        assertThat(policyEngine.isAllowed(policy, student, course)).isTrue();
    }

    @Test
    void testStudentNotEnrolledIsDenied() {
        User student = createUser(Set.of("other-course-students"));
        Course course = createCourse(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, course)).isFalse();
    }

    @Test
    void testTeachingAssistantIsAllowed() {
        User ta = createUser(Set.of("course1-tas"));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, ta, course)).isTrue();
    }

    @Test
    void testEditorIsAllowed() {
        User editor = createUser(Set.of("course1-editors"));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, editor, course)).isTrue();
    }

    @Test
    void testInstructorIsAllowed() {
        User instructor = createUser(Set.of("course1-instructors"));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, instructor, course)).isTrue();
    }

    @Test
    void testAdminIsAllowed() {
        User admin = createUser(Set.of(), Set.of(new Authority(Role.ADMIN.getAuthority())));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, admin, course)).isTrue();
    }

    @Test
    void testSuperAdminIsAllowed() {
        User superAdmin = createUser(Set.of(), Set.of(new Authority(Role.SUPER_ADMIN.getAuthority())));
        Course course = createCourse(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, superAdmin, course)).isTrue();
    }

    @Test
    void testUnenrolledNonAdminUserIsDenied() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.STUDENT.getAuthority())));
        Course course = createCourse(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, user, course)).isFalse();
    }

    @Test
    void testTeachingAssistantCanSeeCourseNotYetStarted() {
        User ta = createUser(Set.of("course1-tas"));
        Course course = createCourse(ZonedDateTime.now().plusDays(30));

        assertThat(policyEngine.isAllowed(policy, ta, course)).isTrue();
    }

    // -- Documentation metadata tests --

    @Test
    void testCourseVisibilityPolicyHasSectionAndFeature() {
        assertThat(policy.getSection()).isEqualTo("Navigation");
        assertThat(policy.getFeature()).isEqualTo("Course Overview");
    }

    @Test
    void testCourseVisibilityPolicyHasDocumentedRoles() {
        List<PolicyRule<Course>> rules = policy.getRules();
        assertThat(rules).hasSize(2);

        // First rule: staff + admins
        PolicyRule<Course> staffRule = rules.get(0);
        assertThat(staffRule.documentedRoles()).containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.ADMIN, Role.INSTRUCTOR, Role.EDITOR, Role.TEACHING_ASSISTANT);
        assertThat(staffRule.note()).isNull();

        // Second rule: students with note
        PolicyRule<Course> studentRule = rules.get(1);
        assertThat(studentRule.documentedRoles()).containsExactly(Role.STUDENT);
        assertThat(studentRule.note()).isEqualTo("if enrolled + started");
    }
}
