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
import de.tum.cit.aet.artemis.core.security.policy.definitions.ProgrammingExerciseAccessPolicies;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ProgrammingExerciseAccessPoliciesTest {

    private AccessPolicy<ProgrammingExercise> policy;

    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        ProgrammingExerciseAccessPolicies config = new ProgrammingExerciseAccessPolicies();
        policy = config.programmingExerciseVisibilityPolicy();
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

    private static ProgrammingExercise createExercise(ZonedDateTime releaseDate) {
        Course course = new Course();
        course.setStudentGroupName("course1-students");
        course.setTeachingAssistantGroupName("course1-tas");
        course.setEditorGroupName("course1-editors");
        course.setInstructorGroupName("course1-instructors");

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setCourse(course);
        exercise.setReleaseDate(releaseDate);
        return exercise;
    }

    @Test
    void testStudentEnrolledReleasedIsAllowed() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, exercise)).isTrue();
    }

    @Test
    void testStudentEnrolledNotReleasedIsDenied() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, exercise)).isFalse();
    }

    @Test
    void testStudentEnrolledNoReleaseDateIsAllowed() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(null);

        assertThat(policyEngine.isAllowed(policy, student, exercise)).isTrue();
    }

    @Test
    void testStudentNotEnrolledIsDenied() {
        User student = createUser(Set.of("other-course-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, student, exercise)).isFalse();
    }

    @Test
    void testTeachingAssistantIsAllowed() {
        User ta = createUser(Set.of("course1-tas"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, ta, exercise)).isTrue();
    }

    @Test
    void testEditorIsAllowed() {
        User editor = createUser(Set.of("course1-editors"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, editor, exercise)).isTrue();
    }

    @Test
    void testInstructorIsAllowed() {
        User instructor = createUser(Set.of("course1-instructors"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, instructor, exercise)).isTrue();
    }

    @Test
    void testAdminIsAllowed() {
        User admin = createUser(Set.of(), Set.of(new Authority(Role.ADMIN.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, admin, exercise)).isTrue();
    }

    @Test
    void testSuperAdminIsAllowed() {
        User superAdmin = createUser(Set.of(), Set.of(new Authority(Role.SUPER_ADMIN.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(policyEngine.isAllowed(policy, superAdmin, exercise)).isTrue();
    }

    @Test
    void testUnenrolledNonAdminUserIsDenied() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.STUDENT.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(policyEngine.isAllowed(policy, user, exercise)).isFalse();
    }

    // -- Documentation metadata tests --

    @Test
    void testPolicyHasSectionAndFeature() {
        assertThat(policy.getSection()).isEqualTo("ProgrammingExercises");
        assertThat(policy.getFeature()).isEqualTo("View Programming Exercise");
    }

    @Test
    void testPolicyHasDocumentedRoles() {
        List<PolicyRule<ProgrammingExercise>> rules = policy.getRules();
        assertThat(rules).hasSize(2);

        // First rule: staff + admins
        PolicyRule<ProgrammingExercise> staffRule = rules.get(0);
        assertThat(staffRule.documentedRoles()).containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.ADMIN, Role.INSTRUCTOR, Role.EDITOR, Role.TEACHING_ASSISTANT);
        assertThat(staffRule.note()).isEqualTo("if in course");

        // Second rule: students with note
        PolicyRule<ProgrammingExercise> studentRule = rules.get(1);
        assertThat(studentRule.documentedRoles()).containsExactly(Role.STUDENT);
        assertThat(studentRule.note()).isEqualTo("if enrolled + released");
    }
}
