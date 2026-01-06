package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseEnrollmentConfiguration;
import de.tum.cit.aet.artemis.core.domain.CourseExtendedSettings;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CourseConfigurationEndpointIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseconfig";

    private static final String EXTENDED_SETTINGS_DESCRIPTION = "course description";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        if (course.getExtendedSettings() == null) {
            course.setExtendedSettings(new CourseExtendedSettings());
        }
        course.getExtendedSettings().setDescription(EXTENDED_SETTINGS_DESCRIPTION);
        course.getExtendedSettings().setMessagingCodeOfConduct("Code of conduct");
        if (course.getEnrollmentConfiguration() == null) {
            course.setEnrollmentConfiguration(new CourseEnrollmentConfiguration());
        }
        course.getEnrollmentConfiguration().setEnrollmentEnabled(true);
        course.getEnrollmentConfiguration().setEnrollmentStartDate(ZonedDateTime.now().minusDays(1));
        course.getEnrollmentConfiguration().setEnrollmentEndDate(ZonedDateTime.now().plusDays(1));
        courseRepository.save(course);

        textExercise = textExerciseUtilService.createSampleTextExercise(course);
        programmingExercise = programmingExerciseUtilService.createProgrammingExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

        var outsider = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        outsider.setGroups(Set.of(TEST_PREFIX + "outsider"));
        userTestRepository.save(outsider);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExerciseIncludesCourseComplaintConfiguration() throws Exception {
        Exercise response = request.get("/api/exercise/exercises/" + textExercise.getId(), HttpStatus.OK, Exercise.class);
        assertComplaintConfigurationLoaded(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExerciseDetailsIncludesCourseComplaintConfiguration() throws Exception {
        ExerciseDetailsDTO response = request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
        assertComplaintConfigurationLoaded(response.exercise());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProgrammingExerciseIncludesCourseComplaintConfiguration() throws Exception {
        ProgrammingExercise response = request.get("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, ProgrammingExercise.class);
        assertComplaintConfigurationLoaded(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseIncludesEnrollmentAndExtendedSettings() throws Exception {
        Course response = request.get("/api/core/courses/" + course.getId(), HttpStatus.OK, Course.class);
        assertEnrollmentConfigurationLoaded(response);
        assertExtendedSettingsLoaded(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseForDashboardIncludesEnrollmentAndExtendedSettings() throws Exception {
        CourseForDashboardDTO response = request.get("/api/core/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class);
        assertEnrollmentConfigurationLoaded(response.course());
        assertExtendedSettingsLoaded(response.course());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getCourseForEnrollmentIncludesExtendedSettings() throws Exception {
        Course response = request.get("/api/core/courses/" + course.getId() + "/for-enrollment", HttpStatus.OK, Course.class);
        assertExtendedSettingsLoaded(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getCoursesForEnrollmentIncludeExtendedSettings() throws Exception {
        var response = request.getList("/api/core/courses/for-enrollment", HttpStatus.OK, Course.class);
        Course enrolledCourse = response.stream().filter(item -> item.getId().equals(course.getId())).findFirst().orElseThrow();
        assertExtendedSettingsLoaded(enrolledCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCoursesForManagementOverviewIncludeExtendedSettings() throws Exception {
        var response = request.getList("/api/core/courses/course-management-overview", HttpStatus.OK, Course.class);
        Course overviewCourse = response.stream().filter(item -> item.getId().equals(course.getId())).findFirst().orElseThrow();
        assertExtendedSettingsLoaded(overviewCourse);
    }

    private void assertComplaintConfigurationLoaded(Exercise exercise) {
        assertThat(exercise.getCourseViaExerciseGroupOrCourseMember()).isNotNull();
        assertThat(exercise.getCourseViaExerciseGroupOrCourseMember().getComplaintConfiguration()).isNotNull();
    }

    private void assertEnrollmentConfigurationLoaded(Course responseCourse) {
        assertThat(responseCourse.getEnrollmentConfiguration()).isNotNull();
        assertThat(responseCourse.getEnrollmentConfiguration().isEnrollmentEnabled()).isTrue();
    }

    private void assertExtendedSettingsLoaded(Course responseCourse) {
        assertThat(responseCourse.getExtendedSettings()).isNotNull();
        assertThat(responseCourse.getExtendedSettings().getDescription()).isEqualTo(EXTENDED_SETTINGS_DESCRIPTION);
    }
}
