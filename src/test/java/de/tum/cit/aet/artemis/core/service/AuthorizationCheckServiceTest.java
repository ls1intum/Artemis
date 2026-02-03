package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class AuthorizationCheckServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "authorizationservice";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private AuthorizationCheckService authCheckService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserTestRepository userRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Nested
    class IsUserAllowedToGetResultTest {

        private ModelingExercise modelingExercise;

        private StudentParticipation participation;

        private Result result;

        @BeforeEach
        void initTestCase() {
            Course course = courseUtilService.addCourseWithModelingAndTextExercise();
            modelingExercise = (ModelingExercise) course.getExercises().iterator().next();

            participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
            participation.setTestRun(true);
            result = new Result();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsAllowedAsInstructorDuringTestRun() {
            boolean isUserAllowedToGetResult = authCheckService.isUserAllowedToGetResult(modelingExercise, participation, result);
            assertThat(isUserAllowedToGetResult).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
        void testIsNotAllowedAsStudentDuringTestRun() {
            boolean isUserAllowedToGetResult = authCheckService.isUserAllowedToGetResult(modelingExercise, participation, result);
            assertThat(isUserAllowedToGetResult).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsNotAllowedAsInstructorForNonTestRunExerciseBeforeDeadline() {
            participation.setTestRun(false);

            boolean isUserAllowedToGetResult = authCheckService.isUserAllowedToGetResult(modelingExercise, participation, result);
            assertThat(isUserAllowedToGetResult).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
        void testIsNotAllowedAsStudentForNonTestRunExerciseBeforeDeadline() {
            participation.setTestRun(false);

            boolean isUserAllowedToGetResult = authCheckService.isUserAllowedToGetResult(modelingExercise, participation, result);
            assertThat(isUserAllowedToGetResult).isFalse();
        }
    }

    @Nested
    class IsAdminTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
        void testIsAdmin_withAdminRole_shouldReturnTrue() {
            boolean isAdmin = authCheckService.isAdmin();
            assertThat(isAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAdmin_withSuperAdminRole_shouldReturnTrue() {
            boolean isAdmin = authCheckService.isAdmin();
            assertThat(isAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsAdmin_withStudentRole_shouldReturnFalse() {
            boolean isAdmin = authCheckService.isAdmin();
            assertThat(isAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
        void testIsAdminWithUser_adminUser_shouldReturnTrue() {
            // Create an admin user by setting authorities manually
            User admin = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            admin.setAuthorities(Set.of(Authority.ADMIN_AUTHORITY, Authority.USER_AUTHORITY));
            admin = userRepository.save(admin);

            boolean isAdmin = authCheckService.isAdmin(admin);
            assertThat(isAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAdminWithUser_superAdminUser_shouldReturnTrue() {
            userUtilService.addSuperAdmin(TEST_PREFIX);
            User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

            boolean isAdmin = authCheckService.isAdmin(superAdmin);
            assertThat(isAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsAdminWithUser_regularUser_shouldReturnFalse() {
            User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

            boolean isAdmin = authCheckService.isAdmin(student);
            assertThat(isAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
        void testIsAdminWithUser_nullUser_shouldUseCurrent() {
            boolean isAdmin = authCheckService.isAdmin((User) null);
            assertThat(isAdmin).isTrue();
        }
    }

    @Nested
    class IsSuperAdminTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsSuperAdmin_withSuperAdminRole_shouldReturnTrue() {
            boolean isSuperAdmin = authCheckService.isSuperAdmin();
            assertThat(isSuperAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
        void testIsSuperAdmin_withAdminRole_shouldReturnFalse() {
            boolean isSuperAdmin = authCheckService.isSuperAdmin();
            assertThat(isSuperAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsSuperAdmin_withStudentRole_shouldReturnFalse() {
            boolean isSuperAdmin = authCheckService.isSuperAdmin();
            assertThat(isSuperAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsSuperAdminWithUser_superAdminUser_shouldReturnTrue() {
            userUtilService.addSuperAdmin(TEST_PREFIX);
            User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

            boolean isSuperAdmin = authCheckService.isSuperAdmin(superAdmin);
            assertThat(isSuperAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsSuperAdminWithUser_regularUser_shouldReturnFalse() {
            User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

            boolean isSuperAdmin = authCheckService.isSuperAdmin(student);
            assertThat(isSuperAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsSuperAdminWithUser_nullUser_shouldUseCurrent() {
            boolean isSuperAdmin = authCheckService.isSuperAdmin((User) null);
            assertThat(isSuperAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsSuperAdminWithLogin_superAdminLogin_shouldReturnTrue() {
            userUtilService.addSuperAdmin(TEST_PREFIX);
            String login = TEST_PREFIX + "superadmin";

            boolean isSuperAdmin = authCheckService.isSuperAdmin(login);
            assertThat(isSuperAdmin).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsSuperAdminWithLogin_regularUserLogin_shouldReturnFalse() {
            String login = TEST_PREFIX + "student1";

            boolean isSuperAdmin = authCheckService.isSuperAdmin(login);
            assertThat(isSuperAdmin).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testCheckIsSuperAdminElseThrow_superAdminUser_shouldNotThrow() {
            userUtilService.addSuperAdmin(TEST_PREFIX);
            User superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

            assertThatCode(() -> authCheckService.checkIsSuperAdminElseThrow(superAdmin)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testCheckIsSuperAdminElseThrow_regularUser_shouldThrow() {
            User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkIsSuperAdminElseThrow(student));
        }
    }

    @Nested
    class StaticIsAdminMethodsTest {

        @Test
        void testIsAdminByAuthorityName_withAdminAuthority_shouldReturnTrue() {
            Set<String> authorities = Set.of(Role.ADMIN.getAuthority());
            boolean isAdmin = AuthorizationCheckService.isAdminByAuthorityName(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdminByAuthorityName_withSuperAdminAuthority_shouldReturnTrue() {
            Set<String> authorities = Set.of(Role.SUPER_ADMIN.getAuthority());
            boolean isAdmin = AuthorizationCheckService.isAdminByAuthorityName(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdminByAuthorityName_withBothAdminAuthorities_shouldReturnTrue() {
            Set<String> authorities = Set.of(Role.ADMIN.getAuthority(), Role.SUPER_ADMIN.getAuthority());
            boolean isAdmin = AuthorizationCheckService.isAdminByAuthorityName(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdminByAuthorityName_withUserAuthority_shouldReturnFalse() {
            Set<String> authorities = Set.of(Role.STUDENT.getAuthority());
            boolean isAdmin = AuthorizationCheckService.isAdminByAuthorityName(authorities);
            assertThat(isAdmin).isFalse();
        }

        @Test
        void testIsAdminByAuthorityName_withEmptySet_shouldReturnFalse() {
            Set<String> authorities = Set.of();
            boolean isAdmin = AuthorizationCheckService.isAdminByAuthorityName(authorities);
            assertThat(isAdmin).isFalse();
        }

        @Test
        void testIsAdminByAuthorityName_withNullSet_shouldReturnFalse() {
            assertThat(AuthorizationCheckService.isAdminByAuthorityName(null)).isFalse();
        }

        @Test
        void testIsAdmin_withAdminAuthority_shouldReturnTrue() {
            Set<Authority> authorities = Set.of(Authority.ADMIN_AUTHORITY);
            boolean isAdmin = AuthorizationCheckService.isAdmin(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdmin_withSuperAdminAuthority_shouldReturnTrue() {
            Set<Authority> authorities = Set.of(Authority.SUPER_ADMIN_AUTHORITY);
            boolean isAdmin = AuthorizationCheckService.isAdmin(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdmin_withBothAdminAuthorities_shouldReturnTrue() {
            Set<Authority> authorities = Set.of(Authority.ADMIN_AUTHORITY, Authority.SUPER_ADMIN_AUTHORITY);
            boolean isAdmin = AuthorizationCheckService.isAdmin(authorities);
            assertThat(isAdmin).isTrue();
        }

        @Test
        void testIsAdmin_withUserAuthority_shouldReturnFalse() {
            Set<Authority> authorities = Set.of(Authority.USER_AUTHORITY);
            boolean isAdmin = AuthorizationCheckService.isAdmin(authorities);
            assertThat(isAdmin).isFalse();
        }

        @Test
        void testIsAdmin_withEmptySet_shouldReturnFalse() {
            Set<Authority> authorities = Set.of();
            boolean isAdmin = AuthorizationCheckService.isAdmin(authorities);
            assertThat(isAdmin).isFalse();
        }

        @Test
        void testIsAdmin_withNullSet_shouldReturnFalse() {
            assertThat(AuthorizationCheckService.isAdmin((Set<Authority>) null)).isFalse();
        }
    }

    @Nested
    class IsAtLeastRoleInCourseWithSuperAdminTest {

        private Course course;

        private User superAdmin;

        private User student;

        private User tutor;

        private User editor;

        private User instructor;

        @BeforeEach
        void setUp() {
            course = courseUtilService.addEmptyCourse();
            userUtilService.addSuperAdmin(TEST_PREFIX);
            superAdmin = userUtilService.getUserByLogin(TEST_PREFIX + "superadmin");

            // Add student to the course's student group
            student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            student.setGroups(Set.of(course.getStudentGroupName()));
            userRepository.save(student);

            // Add tutor to the course's TA group
            tutor = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            tutor.setGroups(Set.of(course.getTeachingAssistantGroupName()));
            userRepository.save(tutor);

            // Create and add editor to the course's editor group
            userUtilService.createAndSaveUser(TEST_PREFIX + "editor");
            editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor");
            editor.setGroups(Set.of(course.getEditorGroupName()));
            userRepository.save(editor);

            // Add instructor to the course's instructor group
            instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            instructor.setGroups(Set.of(course.getInstructorGroupName()));
            userRepository.save(instructor);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAtLeastStudentInCourse_superAdmin_shouldReturnTrue() {
            boolean isAtLeastStudent = authCheckService.isAtLeastStudentInCourse(course, superAdmin);
            assertThat(isAtLeastStudent).as("Super admin should have student-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsAtLeastStudentInCourse_student_shouldReturnTrue() {
            boolean isAtLeastStudent = authCheckService.isAtLeastStudentInCourse(course, student);
            assertThat(isAtLeastStudent).as("Student should have student-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "nonEnrolledStudent", roles = "USER")
        void testIsAtLeastStudentInCourse_nonEnrolledStudent_shouldReturnFalse() {
            userUtilService.createAndSaveUser(TEST_PREFIX + "nonEnrolledStudent");
            User nonEnrolledStudent = userUtilService.getUserByLogin(TEST_PREFIX + "nonEnrolledStudent");
            boolean isAtLeastStudent = authCheckService.isAtLeastStudentInCourse(course, nonEnrolledStudent);
            assertThat(isAtLeastStudent).as("Non-enrolled student should not have student-level access in course").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAtLeastTeachingAssistantInCourse_superAdmin_shouldReturnTrue() {
            boolean isAtLeastTA = authCheckService.isAtLeastTeachingAssistantInCourse(course, superAdmin);
            assertThat(isAtLeastTA).as("Super admin should have TA-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "TA")
        void testIsAtLeastTeachingAssistantInCourse_tutor_shouldReturnTrue() {
            boolean isAtLeastTA = authCheckService.isAtLeastTeachingAssistantInCourse(course, tutor);
            assertThat(isAtLeastTA).as("Tutor should have TA-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsAtLeastTeachingAssistantInCourse_student_shouldReturnFalse() {
            boolean isAtLeastTA = authCheckService.isAtLeastTeachingAssistantInCourse(course, student);
            assertThat(isAtLeastTA).as("Student should not have TA-level access in course").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAtLeastEditorInCourse_superAdmin_shouldReturnTrue() {
            boolean isAtLeastEditor = authCheckService.isAtLeastEditorInCourse(course, superAdmin);
            assertThat(isAtLeastEditor).as("Super admin should have editor-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor", roles = "EDITOR")
        void testIsAtLeastEditorInCourse_editor_shouldReturnTrue() {
            boolean isAtLeastEditor = authCheckService.isAtLeastEditorInCourse(course, editor);
            assertThat(isAtLeastEditor).as("Editor should have editor-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "TA")
        void testIsAtLeastEditorInCourse_tutor_shouldReturnFalse() {
            boolean isAtLeastEditor = authCheckService.isAtLeastEditorInCourse(course, tutor);
            assertThat(isAtLeastEditor).as("Tutor should not have editor-level access in course").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testIsAtLeastInstructorInCourse_superAdmin_shouldReturnTrue() {
            boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorInCourse(course, superAdmin);
            assertThat(isAtLeastInstructor).as("Super admin should have instructor-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsAtLeastInstructorInCourse_instructor_shouldReturnTrue() {
            boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorInCourse(course, instructor);
            assertThat(isAtLeastInstructor).as("Instructor should have instructor-level access in course").isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor", roles = "EDITOR")
        void testIsAtLeastInstructorInCourse_editor_shouldReturnFalse() {
            boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorInCourse(course, editor);
            assertThat(isAtLeastInstructor).as("Editor should not have instructor-level access in course").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testCheckIsAtLeastStudentInCourse_superAdmin_shouldNotThrow() {
            assertThatCode(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, superAdmin)).as("Super admin should pass student-level access check")
                    .doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "nonEnrolledStudent2", roles = "USER")
        void testCheckIsAtLeastStudentInCourse_nonEnrolledStudent_shouldThrow() {
            userUtilService.createAndSaveUser(TEST_PREFIX + "nonEnrolledStudent2");
            User nonEnrolledStudent = userUtilService.getUserByLogin(TEST_PREFIX + "nonEnrolledStudent2");
            assertThatExceptionOfType(AccessForbiddenException.class)
                    .isThrownBy(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, nonEnrolledStudent))
                    .as("Non-enrolled student should fail student-level access check");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testCheckIsAtLeastTeachingAssistantInCourse_superAdmin_shouldNotThrow() {
            assertThatCode(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, superAdmin))
                    .as("Super admin should pass TA-level access check").doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testCheckIsAtLeastTeachingAssistantInCourse_student_shouldThrow() {
            assertThatExceptionOfType(AccessForbiddenException.class)
                    .isThrownBy(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, student))
                    .as("Student should fail TA-level access check");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testCheckIsAtLeastEditorInCourse_superAdmin_shouldNotThrow() {
            assertThatCode(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, superAdmin)).as("Super admin should pass editor-level access check")
                    .doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "TA")
        void testCheckIsAtLeastEditorInCourse_tutor_shouldThrow() {
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, tutor))
                    .as("Tutor should fail editor-level access check");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "superadmin", roles = "SUPER_ADMIN")
        void testCheckIsAtLeastInstructorInCourse_superAdmin_shouldNotThrow() {
            assertThatCode(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, superAdmin))
                    .as("Super admin should pass instructor-level access check").doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor", roles = "EDITOR")
        void testCheckIsAtLeastInstructorInCourse_editor_shouldThrow() {
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, editor))
                    .as("Editor should fail instructor-level access check");
        }
    }
}
