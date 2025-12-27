package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
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
    private CourseTestRepository courseRepository;

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
    // Only the login name of the student2 user is NOT allowed to enrol for courses.
    class IsUserAllowedToEnrollForCourseTest {

        private User student1;

        private Course getCourseForEnrollmentAllowedTest() {
            var course = courseUtilService.createCourse();
            course.setEnrollmentEnabled(true);
            course.setEnrollmentStartDate(ZonedDateTime.now().minusDays(2));
            course.setEnrollmentEndDate(ZonedDateTime.now().plusDays(2));
            course.setStudentGroupName("test-students");
            return course;
        }

        @BeforeEach
        void setUp() {
            this.student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForAllowed() {
            Course course = getCourseForEnrollmentAllowedTest();
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongUsernamePattern() {
            // student2 is not allowed to enroll in courses, see the @TestPropertySource annotation above.
            var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            Course course = getCourseForEnrollmentAllowedTest();
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(student2, course))
                    .withMessage("Enrollment with this username is not allowed.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongEnrollmentStartDate() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("The course does currently not allow enrollment.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongEndDate() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("The course does currently not allow enrollment.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForRegistrationDisabled() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("The course does not allow enrollment.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForDifferentOrganizations() {
            var courseWithOrganizations = courseUtilService.createCourseWithOrganizations();
            // load the user with organizations, otherwise the following check would lead to
            // JpaSystemException: failed to lazily initialize a collection of role: de.tum.cit.aet.artemis.domain.User.organizations
            this.student1 = userRepository.findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(this.student1.getId());
            assertThatExceptionOfType(AccessForbiddenException.class)
                    .isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, courseWithOrganizations))
                    .withMessage("User is not member of any organization of this course.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInOnlineCourse() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEnabled(false);
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course));
        }
    }

    @Nested
    class IsUserAllowedToUnenrollFromCourseTest {

        private Course getCourseForUnenrollmentAllowedTest() {
            var course = courseUtilService.createCourse();
            course.setUnenrollmentEnabled(true);
            course.setEnrollmentStartDate(ZonedDateTime.now().minusDays(2));
            course.setEnrollmentEndDate(ZonedDateTime.now().plusDays(2));
            course.setUnenrollmentEndDate(ZonedDateTime.now().plusDays(4));
            course.setEndDate(ZonedDateTime.now().plusDays(6));
            course.setStudentGroupName("test-students");
            return course;
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentEndSetAndAllowed() {
            Course course = getCourseForUnenrollmentAllowedTest();
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentEndNotSetAndAllowed() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentDisabled() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentStartDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            // unenrollment period starts with enrollment period
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            course.setEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromOnlineCourse() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
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
}
