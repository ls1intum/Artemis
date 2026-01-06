package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.course.CourseAccessService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseAccessServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseAccessservice";

    @Autowired
    private CourseAccessService courseAccessService;

    @Autowired
    private CourseTestRepository courseRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindEnrollableForStudent() {
        var enrollmentDisabled = courseUtilService.createCourse();
        enrollmentDisabled.setStudentGroupName("test-enrollable-students");
        enrollmentDisabled.getEnrollmentConfiguration().setEnrollmentEnabled(false);
        courseRepository.save(enrollmentDisabled);

        var enrollmentEnabledNotActivePast = courseUtilService.createCourse();
        enrollmentEnabledNotActivePast.setStudentGroupName("test-enrollable-students");
        setEnrollmentConfiguration(enrollmentEnabledNotActivePast, ZonedDateTime.now().minusDays(7), ZonedDateTime.now().minusDays(5));
        courseRepository.save(enrollmentEnabledNotActivePast);

        var enrollmentEnabledNotActiveFuture = courseUtilService.createCourse();
        enrollmentEnabledNotActiveFuture.setStudentGroupName("test-enrollable-students");
        setEnrollmentConfiguration(enrollmentEnabledNotActiveFuture, ZonedDateTime.now().plusDays(5), ZonedDateTime.now().plusDays(7));
        courseRepository.save(enrollmentEnabledNotActiveFuture);

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var courses = courseAccessService.findAllEnrollableForUser(student);
        assertThat(courses).doesNotContain(enrollmentDisabled, enrollmentEnabledNotActivePast, enrollmentEnabledNotActiveFuture);

        var enrollmentEnabledAndActive = courseUtilService.createCourse();
        enrollmentEnabledAndActive.setStudentGroupName("test-enrollable-students");
        setEnrollmentConfiguration(enrollmentEnabledAndActive, ZonedDateTime.now().minusDays(5), ZonedDateTime.now().plusDays(5));
        courseRepository.save(enrollmentEnabledAndActive);

        courses = courseAccessService.findAllEnrollableForUser(student);
        assertThat(courses).contains(enrollmentEnabledAndActive);
    }

    private void setEnrollmentConfiguration(Course course, ZonedDateTime start, ZonedDateTime end) {
        course.getEnrollmentConfiguration().setEnrollmentEnabled(true);
        course.getEnrollmentConfiguration().setEnrollmentStartDate(start);
        course.getEnrollmentConfiguration().setEnrollmentEndDate(end);
    }
}
