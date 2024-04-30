package de.tum.in.www1.artemis.learninganalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseTestService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class StudentLearningAnalyticsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "studentlearninganalytics";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private CourseTestService courseTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    private static final int NUMBER_OF_STUDENTS = 5;

    @BeforeEach
    void setupTestScenario() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1).get(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEnableStudentCourseAnalyticsDashboardAsInstructor_badRequest() throws Exception {
        course.setStudentCourseAnalyticsDashboardEnabled(true);
        courseRepository.save(course);

        course.setStudentCourseAnalyticsDashboardEnabled(false);

        request.performMvcRequest(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testEnableStudentCourseAnalyticsDashboardAsAdmin_success() throws Exception {
        course.setStudentCourseAnalyticsDashboardEnabled(true);
        courseRepository.save(course);

        course.setStudentCourseAnalyticsDashboardEnabled(false);

        MvcResult result = request.performMvcRequest(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        Course updatedCourse = objectMapper.readValue(result.getResponse().getContentAsString(), Course.class);

        assertThat(updatedCourse.getStudentCourseAnalyticsDashboardEnabled()).isFalse();
    }
}
