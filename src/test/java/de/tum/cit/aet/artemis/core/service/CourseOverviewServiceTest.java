package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.course.service.CourseOverviewService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseOverviewServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseoverviewservice";

    @Autowired
    private CourseOverviewService courseOverviewService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOverviewAsAdmin() {
        // Minimal testcase: Admins always see all courses
        // Add two courses, one not active
        var course = courseUtilService.addEmptyCourse();
        var inactiveCourse = courseUtilService.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        courseRepository.save(inactiveCourse);

        var courses = courseOverviewService.getAllCoursesForManagementOverview(false);
        assertThat(courses).contains(inactiveCourse, course);

        courses = courseOverviewService.getAllCoursesForManagementOverview(true);
        assertThat(courses).contains(course);
        assertThat(courses).doesNotContain(inactiveCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetOverviewAsInstructor() {
        // Testcase: Instructors see their courses
        // Add three courses, containing one not active and one not belonging to the instructor
        var course = courseUtilService.addEmptyCourse();
        var inactiveCourse = courseUtilService.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        inactiveCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(inactiveCourse);
        var instructorsCourse = courseUtilService.createCourse();
        courseRepository.save(instructorsCourse);

        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, inactiveCourse, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(instructor, instructorsCourse, CourseRole.INSTRUCTOR);

        var courses = courseOverviewService.getAllCoursesForManagementOverview(false);
        assertThat(courses).contains(instructorsCourse);
        assertThat(courses).contains(inactiveCourse);
        assertThat(courses).doesNotContain(course);

        courses = courseOverviewService.getAllCoursesForManagementOverview(true);
        assertThat(courses).contains(instructorsCourse);
        assertThat(courses).doesNotContain(inactiveCourse);
        assertThat(courses).doesNotContain(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOverviewAsStudent() {
        // Testcase: Students should not see courses
        // Add three courses, containing one not active and one not belonging to the student
        courseUtilService.addEmptyCourse();
        var inactiveCourse = courseUtilService.createCourse();
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(7));
        inactiveCourse.setStudentGroupName("test-students");
        courseRepository.save(inactiveCourse);
        var instructorsCourse = courseUtilService.createCourse();
        courseRepository.save(instructorsCourse);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.enrollUserInCourse(student, inactiveCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(student, instructorsCourse, CourseRole.STUDENT);

        var courses = courseOverviewService.getAllCoursesForManagementOverview(false);
        assertThat(courses).isEmpty();

        courses = courseOverviewService.getAllCoursesForManagementOverview(true);
        assertThat(courses).isEmpty();
    }
}
