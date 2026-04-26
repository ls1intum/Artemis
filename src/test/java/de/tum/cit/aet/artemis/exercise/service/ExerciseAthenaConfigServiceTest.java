package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExerciseAthenaConfigServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseathenaconfigservice";

    @Autowired
    private ExerciseAthenaConfigService exerciseAthenaConfigService;

    @Autowired
    private ExerciseAthenaConfigRepository exerciseAthenaConfigRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ModelingExercise exercise;

    @BeforeEach
    void init() {
        var course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
    }

    @Test
    void createOrUpdateConfig_createsNewConfigWhenNoneExists() {
        // Arrange
        String preliminaryModule = "preliminary-module";
        String gradedModule = "graded-module";

        // Act
        ExerciseAthenaConfig result = exerciseAthenaConfigService.createOrUpdateConfig(exercise, preliminaryModule, gradedModule);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getExercise()).isEqualTo(exercise);
        assertThat(result.getPreliminaryFeedbackModule()).isEqualTo(preliminaryModule);
        assertThat(result.getGradedFeedbackModule()).isEqualTo(gradedModule);

        // Verify it was saved to the database
        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(exercise.getId());
        assertThat(savedConfig).isPresent();
        assertThat(savedConfig.get().getPreliminaryFeedbackModule()).isEqualTo(preliminaryModule);
        assertThat(savedConfig.get().getGradedFeedbackModule()).isEqualTo(gradedModule);
    }

    @Test
    void createOrUpdateConfig_updatesExistingConfig() {
        // Arrange - create initial config
        String initialPreliminaryModule = "initial-preliminary-module";
        String initialGradedModule = "initial-graded-module";
        exerciseAthenaConfigService.createOrUpdateConfig(exercise, initialPreliminaryModule, initialGradedModule);

        // Act - update the config
        String updatedPreliminaryModule = "updated-preliminary-module";
        String updatedGradedModule = "updated-graded-module";
        ExerciseAthenaConfig result = exerciseAthenaConfigService.createOrUpdateConfig(exercise, updatedPreliminaryModule, updatedGradedModule);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getExercise()).isEqualTo(exercise);
        assertThat(result.getPreliminaryFeedbackModule()).isEqualTo(updatedPreliminaryModule);
        assertThat(result.getGradedFeedbackModule()).isEqualTo(updatedGradedModule);

        // Verify it was updated in the database
        var savedConfig = exerciseAthenaConfigRepository.findByExerciseId(exercise.getId());
        assertThat(savedConfig).isPresent();
        assertThat(savedConfig.get().getPreliminaryFeedbackModule()).isEqualTo(updatedPreliminaryModule);
        assertThat(savedConfig.get().getGradedFeedbackModule()).isEqualTo(updatedGradedModule);

        // Verify only one config exists for this exercise
        var allConfigs = exerciseAthenaConfigRepository.findAll();
        assertThat(allConfigs).hasSize(1);
    }
}
