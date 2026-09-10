package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.course.service.CourseAthenaConfigService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the course-level Athena configuration endpoints backing the toggles on the course overview and in the
 * onboarding wizard.
 */
class CourseAthenaConfigResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenacourseconfig";

    @Autowired
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    @Autowired
    private CourseAthenaConfigService courseAthenaConfigService;

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
        return CourseAthenaConfigDTO.from(courseAthenaConfigRepository.findFeedbackSettingsByCourseId(course.getId()).orElseThrow());
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

        var updated = request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(true, true));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_courseWithoutConfig_createsIt() throws Exception {
        var updated = request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(false, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(false, true));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(false, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_switchingOneFlagKeepsTheOther() throws Exception {
        persistAthenaConfig(true, false);

        request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(null, true), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_omittedFlagIsNotWritten() throws Exception {
        persistAthenaConfig(true, true);

        // What a client that only ever sends the feature it switched off looks like: the omitted grading flag has to
        // survive, even though this request carries no value for it at all.
        var updated = request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(null, false), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(true, false));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_emptyUpdate_changesNothing() throws Exception {
        persistAthenaConfig(true, false);

        var updated = request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(null, null), CourseAthenaConfigDTO.class, HttpStatus.OK);

        assertThat(updated).isEqualTo(new CourseAthenaConfigDTO(true, false));
        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_concurrentSwitchesOfDifferentFeaturesBothSurvive() throws Exception {
        persistAthenaConfig(false, false);

        // Each update names only its own feature, so neither can carry the other's stale value back into the database.
        // Called on the service because the mocked user of an HTTP request does not carry over to another thread.
        var barrier = new CyclicBarrier(2);
        Callable<CourseAthenaConfigDTO> enableGrading = () -> {
            barrier.await();
            return courseAthenaConfigService.updateConfig(course.getId(), new CourseAthenaConfigUpdateDTO(true, null));
        };
        Callable<CourseAthenaConfigDTO> enableFormative = () -> {
            barrier.await();
            return courseAthenaConfigService.updateConfig(course.getId(), new CourseAthenaConfigUpdateDTO(null, true));
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (Future<CourseAthenaConfigDTO> result : executor.invokeAll(List.of(enableGrading, enableFormative))) {
                result.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(storedConfig()).isEqualTo(new CourseAthenaConfigDTO(true, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void ensureAthenaConfigExists_concurrentFirstUpdates_shareOneConfiguration() throws Exception {
        // A course from before the configuration existed has a null athena_config_id, so two instructors switching a
        // feature at the same time both reach the create path. Without the course-row lock each created its own
        // configuration and they raced to point the course at it, leaving the loser's toggle in a row nothing
        // references any more - answered with 200, stored nowhere the course can see.
        assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).isEmpty();

        var barrier = new CyclicBarrier(2);
        Callable<Long> initialize = () -> {
            barrier.await();
            return courseAthenaConfigRepository.ensureAthenaConfigExists(course.getId());
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results = executor.invokeAll(List.of(initialize, initialize));
            long firstConfigId = results.get(0).get();
            long secondConfigId = results.get(1).get();

            assertThat(firstConfigId).isEqualTo(secondConfigId);
            assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).contains(firstConfigId);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAthenaConfig_asTutor_isForbidden() throws Exception {
        request.get(configPath, HttpStatus.FORBIDDEN, CourseAthenaConfigDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAthenaConfig_asTutor_isForbidden() throws Exception {
        request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateAthenaConfig_asStudent_isForbidden() throws Exception {
        request.patchWithResponseBody(configPath, new CourseAthenaConfigUpdateDTO(true, true), CourseAthenaConfigDTO.class, HttpStatus.FORBIDDEN);
    }
}
