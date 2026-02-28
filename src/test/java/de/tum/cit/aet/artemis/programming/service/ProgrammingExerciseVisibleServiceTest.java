package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;
import de.tum.cit.aet.artemis.core.security.policy.definitions.ProgrammingExerciseAccessPolicies;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ProgrammingExerciseVisibleServiceTest {

    private ProgrammingExerciseVisibleService visibleService;

    @BeforeEach
    void setUp() {
        ProgrammingExerciseAccessPolicies config = new ProgrammingExerciseAccessPolicies();
        PolicyEngine policyEngine = new PolicyEngine();
        visibleService = new ProgrammingExerciseVisibleService(policyEngine, config.programmingExerciseVisibilityPolicy());
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
    void testStudentEnrolledReleasedIsVisible() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(visibleService.isVisibleForUser(student, exercise)).isTrue();
    }

    @Test
    void testStudentEnrolledNotReleasedIsNotVisible() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(student, exercise)).isFalse();
    }

    @Test
    void testStudentEnrolledNoReleaseDateIsVisible() {
        User student = createUser(Set.of("course1-students"));
        ProgrammingExercise exercise = createExercise(null);

        assertThat(visibleService.isVisibleForUser(student, exercise)).isTrue();
    }

    @Test
    void testStudentNotEnrolledIsNotVisible() {
        User student = createUser(Set.of("other-course-students"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(visibleService.isVisibleForUser(student, exercise)).isFalse();
    }

    @Test
    void testTeachingAssistantIsVisible() {
        User ta = createUser(Set.of("course1-tas"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(ta, exercise)).isTrue();
    }

    @Test
    void testEditorIsVisible() {
        User editor = createUser(Set.of("course1-editors"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(editor, exercise)).isTrue();
    }

    @Test
    void testInstructorIsVisible() {
        User instructor = createUser(Set.of("course1-instructors"));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(instructor, exercise)).isTrue();
    }

    @Test
    void testAdminIsVisible() {
        User admin = createUser(Set.of(), Set.of(new Authority(Role.ADMIN.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(admin, exercise)).isTrue();
    }

    @Test
    void testSuperAdminIsVisible() {
        User superAdmin = createUser(Set.of(), Set.of(new Authority(Role.SUPER_ADMIN.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().plusDays(1));

        assertThat(visibleService.isVisibleForUser(superAdmin, exercise)).isTrue();
    }

    @Test
    void testUnenrolledNonAdminUserIsNotVisible() {
        User user = createUser(Set.of(), Set.of(new Authority(Role.STUDENT.getAuthority())));
        ProgrammingExercise exercise = createExercise(ZonedDateTime.now().minusDays(1));

        assertThat(visibleService.isVisibleForUser(user, exercise)).isFalse();
    }
}
