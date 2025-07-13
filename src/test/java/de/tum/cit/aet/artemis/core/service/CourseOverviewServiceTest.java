package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.course.CourseOverviewService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseOverviewServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseoverviewservice";

    @Autowired
    private CourseOverviewService courseOverviewService;

    @Autowired
    private UserTestRepository userRepository;

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
        instructorsCourse.setInstructorGroupName("test-instructors");
        courseRepository.save(instructorsCourse);

        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var groups = new HashSet<String>();
        groups.add("test-instructors");
        instructor.setGroups(groups);
        userRepository.save(instructor);

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
        instructorsCourse.setStudentGroupName("test-students");
        courseRepository.save(instructorsCourse);

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var groups = new HashSet<String>();
        groups.add("test-students");
        student.setGroups(groups);
        userRepository.save(student);

        var courses = courseOverviewService.getAllCoursesForManagementOverview(false);
        assertThat(courses).isEmpty();

        courses = courseOverviewService.getAllCoursesForManagementOverview(true);
        assertThat(courses).isEmpty();
    }
}
