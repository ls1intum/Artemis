package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

@TestPropertySource(properties = { "artemis.user-management.course-enrollment.allowed-username-pattern=^(?!enrollmentservicestudent2).*$" })
class EnrollmentServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "enrollmentservice";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private UserTestRepository userRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
    }

    @Nested
    // Only the login name of the student2 user is NOT allowed to enroll for courses.
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
            assertThatCode(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongUsernamePattern() {
            // student2 is not allowed to enroll in courses, see the @TestPropertySource annotation above.
            var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            Course course = getCourseForEnrollmentAllowedTest();
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(student2, course))
                    .withMessage("Enrollment with this username is not allowed.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongEnrollmentStartDate() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("The course does currently not allow enrollment.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForWrongEndDate() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("The course does currently not allow enrollment.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInCourseForRegistrationDisabled() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
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
                    .isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, courseWithOrganizations))
                    .withMessage("User is not member of any organization of this course.");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToEnrollInOnlineCourse() {
            Course course = getCourseForEnrollmentAllowedTest();
            course.setEnrollmentEnabled(true);
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(this.student1, course))
                    .withMessage("Online courses cannot be enrolled in.");
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
            assertThatCode(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentEndNotSetAndAllowed() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            courseRepository.save(course);
            assertThatCode(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForUnenrollmentDisabled() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentStartDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            // unenrollment period starts with enrollment period
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongUnenrollmentEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromCourseForWrongEndDate() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            course.setEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToUnenrollFromOnlineCourse() {
            Course course = getCourseForUnenrollmentAllowedTest();
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course));
        }
    }
}
