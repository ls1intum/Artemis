package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

class AuthorizationCheckServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "authorizationservice";

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Nested
    @Component
    // The following annotation can only be applied to classes: https://github.com/spring-projects/spring-framework/issues/18951
    // Only the login name of the student2 user is NOT allowed to self-register for courses.
    @TestPropertySource(properties = "artemis.user-management.course-registration.allowed-username-pattern=^(?!" + TEST_PREFIX + "student2).*$")
    class IsUserAllowedToSelfRegisterForCourseTest {

        // We need our own courseService here that overshadows the one from the CourseServiceTest, so that the new property is applied to it.
        @Autowired
        private AuthorizationCheckService authCheckService;

        @Autowired
        private CourseRepository courseRepository;

        private User student1;

        private Course getCourseForSelfRegistrationAllowedTest() {
            var course = database.createCourse();
            course.setRegistrationEnabled(true);
            course.setStartDate(ZonedDateTime.now().minusDays(2));
            course.setEndDate(ZonedDateTime.now().plusDays(2));
            course.setStudentGroupName("test-students");
            return course;
        }

        @BeforeEach
        void setUp() {
            this.student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForAllowed() {
            Course course = getCourseForSelfRegistrationAllowedTest();
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForWrongUsernamePattern() {
            // student2 is not allowed to self-register for courses, see the @TestPropertySource annotation above.
            var student2 = database.getUserByLogin(TEST_PREFIX + "student2");
            Course course = getCourseForSelfRegistrationAllowedTest();
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(student2, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForWrongStartDate() {
            Course course = getCourseForSelfRegistrationAllowedTest();
            course.setStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForWrongEndDate() {
            Course course = getCourseForSelfRegistrationAllowedTest();
            course.setEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForRegistrationDisabled() {
            Course course = getCourseForSelfRegistrationAllowedTest();
            course.setRegistrationEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForCourseForDifferentOrganizations() {
            var courseWithOrganizations = database.createCourseWithOrganizations();
            assertThatExceptionOfType(AccessForbiddenException.class)
                    .isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, courseWithOrganizations));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfRegisterForOnlineCourse() {
            Course course = getCourseForSelfRegistrationAllowedTest();
            course.setRegistrationEnabled(false);
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfRegisterForCourseElseThrow(this.student1, course));
        }
    }

    @Nested
    @Component
    class IsUserAllowedToSelfUnenrollFromCourseTest {

        @Autowired
        private AuthorizationCheckService authCheckService;

        @Autowired
        private CourseRepository courseRepository;

        private User student;

        private Course getCourseForSelfUnenrollmentAllowedTest() {
            var course = database.createCourse();
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
            this.student = database.getUserByLogin(TEST_PREFIX + "student1");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForUnenrollmentEndSetAndAllowed() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForUnenrollmentEndNotSetAndAllowed() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            courseRepository.save(course);
            assertThatCode(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course)).doesNotThrowAnyException();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForUnenrollmentDisabled() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            course.setUnenrollmentEnabled(false);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForWrongUnenrollmentStartDate() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            // unenrollment period starts with enrollment period
            course.setEnrollmentStartDate(ZonedDateTime.now().plusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForWrongUnenrollmentEndDate() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromCourseForWrongEndDate() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            course.setUnenrollmentEndDate(null);
            course.setEndDate(ZonedDateTime.now().minusDays(1));
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(student, course));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testIsUserAllowedToSelfUnenrollFromOnlineCourse() {
            Course course = getCourseForSelfUnenrollmentAllowedTest();
            course.setOnlineCourse(true);
            courseRepository.save(course);
            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> authCheckService.checkUserAllowedToSelfUnenrollFromCourseElseThrow(this.student, course));
        }
    }
}
