package de.tum.cit.aet.artemis.core.user;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrepotest";

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Test
    void testFindAllNotEnrolledUsers() {
        List<User> expected = userRepository
                .saveAll(userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(), 1, 3));
        // Should not find administrators
        List<User> unexpected = userRepository.saveAll(
                userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(Authority.ADMIN_AUTHORITY), 4, 4));
        // Should not find deleted users
        List<User> deleted = userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(), 5, 6);
        deleted.forEach(user -> user.setDeleted(true));
        unexpected.addAll(userRepository.saveAll(deleted));

        final List<String> actual = userRepository.findAllNotEnrolledUsers();

        assertThat(actual).doesNotContainAnyElementsOf(unexpected.stream().map(User::getLogin).toList());
        assertThat(actual).containsAll(expected.stream().map(User::getLogin).toList());
    }

    @Test
    void testIsSuperAdmin() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a regular admin user
        User admin = userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
        admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY));
        admin = userRepository.save(admin);

        // Create a regular user
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin is correctly identified
        assertThat(userRepository.isSuperAdmin(superAdmin.getLogin())).isTrue();

        // Test that regular admin is not identified as super admin
        assertThat(userRepository.isSuperAdmin(admin.getLogin())).isFalse();

        // Test that regular user is not identified as super admin
        assertThat(userRepository.isSuperAdmin(regularUser.getLogin())).isFalse();

        // Test with non-existent user
        assertThat(userRepository.isSuperAdmin("nonexistentuser")).isFalse();
    }

    @Test
    void testSuperAdminHasAccessToCourse() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a course without the super admin being enrolled
        Course course = courseUtilService.createCourse();

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin has access
        assertThat(userRepository.isAtLeastStudentInCourse(superAdmin.getLogin(), course.getId())).isTrue();
        assertThat(userRepository.isAtLeastTeachingAssistantInCourse(superAdmin.getLogin(), course.getId())).isTrue();
        assertThat(userRepository.isAtLeastEditorInCourse(superAdmin.getLogin(), course.getId())).isTrue();
        assertThat(userRepository.isAtLeastInstructorInCourse(superAdmin.getLogin(), course.getId())).isTrue();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInCourse(regularUser.getLogin(), course.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInCourse(regularUser.getLogin(), course.getId())).isFalse();
    }

    @Test
    void testSuperAdminHasAccessToExercise() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a course and exercise without the super admin being enrolled
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin has access
        assertThat(userRepository.isAtLeastStudentInExercise(superAdmin.getLogin(), exercise.getId())).isTrue();
        assertThat(userRepository.isAtLeastTeachingAssistantInExercise(superAdmin.getLogin(), exercise.getId())).isTrue();
        assertThat(userRepository.isAtLeastEditorInExercise(superAdmin.getLogin(), exercise.getId())).isTrue();
        assertThat(userRepository.isAtLeastInstructorInExercise(superAdmin.getLogin(), exercise.getId())).isTrue();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInExercise(regularUser.getLogin(), exercise.getId())).isFalse();
    }

    @Test
    void testSuperAdminHasAccessToParticipation() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a course, exercise, and participation without the super admin being enrolled
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        Exercise exercise = course.getExercises().iterator().next();
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "student");
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, student.getLogin());

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin has access
        assertThat(userRepository.isAtLeastStudentInParticipation(superAdmin.getLogin(), participation.getId())).isTrue();
        assertThat(userRepository.isAtLeastTeachingAssistantInParticipation(superAdmin.getLogin(), participation.getId())).isTrue();
        assertThat(userRepository.isAtLeastEditorInParticipation(superAdmin.getLogin(), participation.getId())).isTrue();
        assertThat(userRepository.isAtLeastInstructorInParticipation(superAdmin.getLogin(), participation.getId())).isTrue();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInParticipation(regularUser.getLogin(), participation.getId())).isFalse();
    }

    @Test
    void testSuperAdminHasAccessToLecture() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a course and lecture without the super admin being enrolled
        Course course = courseUtilService.createCourse();
        Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin has access
        assertThat(userRepository.isAtLeastStudentInLecture(superAdmin.getLogin(), lecture.getId())).isTrue();
        assertThat(userRepository.isAtLeastTeachingAssistantInLecture(superAdmin.getLogin(), lecture.getId())).isTrue();
        assertThat(userRepository.isAtLeastEditorInLecture(superAdmin.getLogin(), lecture.getId())).isTrue();
        assertThat(userRepository.isAtLeastInstructorInLecture(superAdmin.getLogin(), lecture.getId())).isTrue();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLecture(regularUser.getLogin(), lecture.getId())).isFalse();
    }

    @Test
    void testSuperAdminHasAccessToLectureUnit() {
        // Create a super admin user
        userUtilService.addSuperAdmin(TEST_PREFIX);
        User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

        // Create a course, lecture, and lecture unit without the super admin being enrolled
        Course course = courseUtilService.createCourse();
        Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        LectureUnit lectureUnit = lectureUtilService.createTextUnit(lecture);

        // Create a regular user who is not enrolled
        User regularUser = userUtilService.createAndSaveUser(TEST_PREFIX + "regularuser");

        // Test that super admin has access
        assertThat(userRepository.isAtLeastStudentInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isTrue();
        assertThat(userRepository.isAtLeastTeachingAssistantInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isTrue();
        assertThat(userRepository.isAtLeastEditorInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isTrue();
        assertThat(userRepository.isAtLeastInstructorInLectureUnit(superAdmin.getLogin(), lectureUnit.getId())).isTrue();

        // Verify regular user does not have access
        assertThat(userRepository.isAtLeastStudentInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastTeachingAssistantInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastEditorInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
        assertThat(userRepository.isAtLeastInstructorInLectureUnit(regularUser.getLogin(), lectureUnit.getId())).isFalse();
    }
}
