package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingExerciseResourceAthenaConfigTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progathena";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExerciseAthenaConfigRepository exerciseAthenaConfigRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
    }

    @AfterEach
    void tearDown() {
        // Delete only the course created in @BeforeEach; cascade removes exercises and their Athena configs.
        courseRepository.delete(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateExerciseWithAthenaConfig() throws Exception {
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setPackageName("de.tum.cit.aet.artemis.test");

        var athenaConfig = new ExerciseAthenaConfig();
        athenaConfig.setPreliminaryFeedbackModule("module_programming_preliminary");
        athenaConfig.setGradedFeedbackModule("module_programming_graded");
        programmingExercise.setAthenaConfig(athenaConfig);

        var response = request.postWithResponseBody("/api/programming/programming-exercises/setup", programmingExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getAthenaConfig()).isNotNull();
        assertThat(response.getAthenaConfig().getPreliminaryFeedbackModule()).isEqualTo("module_programming_preliminary");
        assertThat(response.getAthenaConfig().getGradedFeedbackModule()).isEqualTo("module_programming_graded");

        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(response.getId());
        assertThat(savedConfig).isPresent();
        assertThat(savedConfig.get().getPreliminaryFeedbackModule()).isEqualTo("module_programming_preliminary");
        assertThat(savedConfig.get().getGradedFeedbackModule()).isEqualTo("module_programming_graded");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateExerciseWithoutAthenaConfig() throws Exception {
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setPackageName("de.tum.cit.aet.artemis.test");

        var response = request.postWithResponseBody("/api/programming/programming-exercises/setup", programmingExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getAthenaConfig()).isNull();

        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(response.getId());
        assertThat(savedConfig).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateExerciseAthenaConfig() throws Exception {
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setPackageName("de.tum.cit.aet.artemis.test");

        var createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", programmingExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        var athenaConfig = new ExerciseAthenaConfig();
        athenaConfig.setPreliminaryFeedbackModule("module_programming_preliminary");
        athenaConfig.setGradedFeedbackModule("module_programming_graded");
        createdExercise.setAthenaConfig(athenaConfig);

        var updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises/" + createdExercise.getId(), createdExercise, ProgrammingExercise.class,
                HttpStatus.OK);

        assertThat(updatedExercise).isNotNull();
        assertThat(updatedExercise.getAthenaConfig()).isNotNull();
        assertThat(updatedExercise.getAthenaConfig().getPreliminaryFeedbackModule()).isEqualTo("module_programming_preliminary");
        assertThat(updatedExercise.getAthenaConfig().getGradedFeedbackModule()).isEqualTo("module_programming_graded");

        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(updatedExercise.getId());
        assertThat(savedConfig).isPresent();
        assertThat(savedConfig.get().getPreliminaryFeedbackModule()).isEqualTo("module_programming_preliminary");
        assertThat(savedConfig.get().getGradedFeedbackModule()).isEqualTo("module_programming_graded");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateExistingAthenaConfig() throws Exception {
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setPackageName("de.tum.cit.aet.artemis.test");

        var athenaConfig = new ExerciseAthenaConfig();
        athenaConfig.setPreliminaryFeedbackModule("module_programming_preliminary");
        athenaConfig.setGradedFeedbackModule("module_programming_graded");
        programmingExercise.setAthenaConfig(athenaConfig);

        var createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", programmingExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        var updatedAthenaConfig = new ExerciseAthenaConfig();
        updatedAthenaConfig.setPreliminaryFeedbackModule("module_programming_graded");
        updatedAthenaConfig.setGradedFeedbackModule("module_programming_preliminary");
        createdExercise.setAthenaConfig(updatedAthenaConfig);

        var updatedExercise = request.putWithResponseBody("/api/programming/programming-exercises/" + createdExercise.getId(), createdExercise, ProgrammingExercise.class,
                HttpStatus.OK);

        assertThat(updatedExercise).isNotNull();
        assertThat(updatedExercise.getAthenaConfig()).isNotNull();
        assertThat(updatedExercise.getAthenaConfig().getPreliminaryFeedbackModule()).isEqualTo("module_programming_graded");
        assertThat(updatedExercise.getAthenaConfig().getGradedFeedbackModule()).isEqualTo("module_programming_preliminary");

        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(updatedExercise.getId());
        assertThat(savedConfig).isPresent();
        assertThat(savedConfig.get().getPreliminaryFeedbackModule()).isEqualTo("module_programming_graded");
        assertThat(savedConfig.get().getGradedFeedbackModule()).isEqualTo("module_programming_preliminary");
    }
}
