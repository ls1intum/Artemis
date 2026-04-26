package de.tum.cit.aet.artemis.exercise.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration tests for {@link ExerciseAthenaConfigRepository}.
 */
class ExerciseAthenaConfigRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseathenaconfig";

    @Autowired
    private ExerciseAthenaConfigRepository exerciseAthenaConfigRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) course.getExercises().iterator().next();
    }

    @Test
    void testFindByExerciseId_returnsConfigWhenPresent() {
        ExerciseAthenaConfig config = new ExerciseAthenaConfig();
        config.setExercise(textExercise);
        config.setPreliminaryFeedbackModule("module-text");
        config.setGradedFeedbackModule("module-text-graded");
        exerciseAthenaConfigRepository.save(config);

        Optional<ExerciseAthenaConfig> result = exerciseAthenaConfigRepository.findByExerciseId(textExercise.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPreliminaryFeedbackModule()).isEqualTo("module-text");
        assertThat(result.get().getGradedFeedbackModule()).isEqualTo("module-text-graded");
    }

    @Test
    void testFindByExerciseId_returnsEmptyWhenAbsent() {
        Optional<ExerciseAthenaConfig> result = exerciseAthenaConfigRepository.findByExerciseId(textExercise.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void testDeleteByExerciseId_removesConfig() {
        ExerciseAthenaConfig config = new ExerciseAthenaConfig();
        config.setExercise(textExercise);
        config.setPreliminaryFeedbackModule("module-text");
        exerciseAthenaConfigRepository.save(config);

        assertThat(exerciseAthenaConfigRepository.findByExerciseId(textExercise.getId())).isPresent();

        exerciseAthenaConfigRepository.deleteByExerciseId(textExercise.getId());

        assertThat(exerciseAthenaConfigRepository.findByExerciseId(textExercise.getId())).isEmpty();
    }
}
