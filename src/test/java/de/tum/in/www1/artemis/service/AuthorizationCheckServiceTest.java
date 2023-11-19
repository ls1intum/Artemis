package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

class AuthorizationCheckServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "authorizationservice";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private AuthorizationCheckService authCheckService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Nested
    @Component
    class IsUserAllowedToGetResultTest {

        @Autowired
        private ParticipationUtilService participationUtilService;

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
    @Component
    // Only the login name of the student2 user is NOT allowed to enrol for courses.
    class IsUserAllowedToEnrollForCourseTest {

        // We need our own courseService here that overshadows the one from the CourseServiceTest, so that the new property is applied to it.
        @Autowired
        private CourseRepository courseRepository;

        @Autowired
        private UserRepository userRepository;

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
            // JpaSystemException: failed to lazily initialize a collection of role: de.tum.in.www1.artemis.domain.User.organizations
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
    @Component
    class IsUserAllowedToUnenrollFromCourseTest {

        @Autowired
        private AuthorizationCheckService authCheckService;

        @Autowired
        private CourseRepository courseRepository;

        private User student;

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

        @BeforeEach
        void setUp() {
            this.student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentEndSetAndAllowed() {
            Course course = getCourseForUnenrollmentAllowedTest();
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentEndNotSetAndAllowed() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentDisabled() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentStartDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            // unenrollment period starts with enrollment period
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            course.setEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromOnlineCourse() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(this.student, course));
        }
    }
}
