package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the course-level Athena configuration endpoints backing the toggles on the course overview and in the
 * onboarding wizard.
 */
class CourseAthenaConfigResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenacourseconfig";

    private Course course;

    private String configPath;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        configPath = "/api/course/courses/" + course.getId() + "/athena-configuration";
    }

    private void persistAthenaConfig(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled) {
        Course persisted = courseRepository.findByIdElseThrow(course.getId());
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setCourse(persisted);
        athenaConfig.setGradingFeedbackEnabled(gradingFeedbackEnabled);
        athenaConfig.setFormativeFeedbackEnabled(formativeFeedbackEnabled);
        persisted.setAthenaConfig(athenaConfig);
        courseRepository.save(persisted);
    }

    private CourseAthenaConfigDTO storedConfig() {
        return CourseAthenaConfigDTO.from(courseRepository.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(course.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAthenaConfig_returnsPersistedFlags() throws Exception {
        persistAthenaConfig(true, false);

        var config = request.get(configPath, HttpStatus.OK, CourseAthenaConfigDTO.class);

        assertThat(config).isEqualTo(new CourseAthenaConfigDTO(true, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAthenaConfig_courseWithoutConfig_returnsBothDisabled() throws Exception {
        var config = request.get(configPath, HttpStatus.OK, CourseAthenaConfigDTO.class);

        assertThat(config).isEqualTo(new CourseAthenaConfigDTO(false, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_persistsBothFlags() throws Exception {
        persistAthenaConfig(false, false);

        var updated = request.putWithResponseBody(configPath, new CourseAthenaConfigDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(true, true));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_courseWithoutConfig_createsIt() throws Exception {
        var updated = request.putWithResponseBody(configPath, new CourseAthenaConfigDTO(false, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(false, true));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(false, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_switchingOneFlagKeepsTheOther() throws Exception {
        persistAthenaConfig(true, false);

        request.putWithResponseBody(configPath, new CourseAthenaConfigDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAthenaConfig_asTutor_isForbidden() throws Exception {
        request.get(configPath, HttpStatus.FORBIDDEN, CourseAthenaConfigDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAthenaConfig_asTutor_isForbidden() throws Exception {
        request.putWithResponseBody(configPath, new CourseAthenaConfigDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateAthenaConfig_asStudent_isForbidden() throws Exception {
        request.putWithResponseBody(configPath, new CourseAthenaConfigDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.FORBIDDEN);
    }
}
