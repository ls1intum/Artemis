package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;

class PlagiarismDetectionConfigHelperTest {

    private static final String ENTITY_NAME = "exercise";

    private static ModelingExercise createExerciseWithConfig() {
        var exercise = new ModelingExercise();
        exercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        return exercise;
    }

    @Test
    void shouldDoNothingIfCourseExerciseHasPlagiarismDetectionConfig() {
        // given: course exercise with PlagiarismDetectionConfig
        var exercise = new ModelingExercise();
        exercise.setCourse(new Course());
        var config = PlagiarismDetectionConfig.createDefault();
        exercise.setPlagiarismDetectionConfig(config);

        // and
        var repository = mock(ModelingExerciseRepository.class);

        // when
        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, repository);

        // then
        verifyNoMoreInteractions(repository);
        assertThat(exercise.getPlagiarismDetectionConfig()).isSameAs(config);
    }

    @Test
    void shouldDoNothingIfExamExercise() {
        // given: exam exercise without PlagiarismDetectionConfig
        var exercise = new ModelingExercise();
        exercise.setExerciseGroup(new ExerciseGroup());

        // and
        var repository = mock(ModelingExerciseRepository.class);

        // when
        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, repository);

        // then
        verifyNoMoreInteractions(repository);
        assertThat(exercise.getPlagiarismDetectionConfig()).isNull();
    }

    @Test
    void shouldAddDefaultConfigIfExerciseDoesNotHavePlagiarismDetectionConfig() {
        // given: course exercise without PlagiarismDetectionConfig
        var exercise = new ModelingExercise();
        exercise.setCourse(new Course());

        // and
        var repository = mock(ModelingExerciseRepository.class);

        // when
        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(exercise, repository);

        // then
        verify(repository).save(exercise);
        assertThat(exercise.getPlagiarismDetectionConfig()).usingRecursiveComparison().isEqualTo(PlagiarismDetectionConfig.createDefault());
    }

    @Test
    void shouldReplaceConfigWithGivenValues() {
        // given: exercise without PlagiarismDetectionConfig
        var exercise = new ModelingExercise();
        var config = PlagiarismDetectionConfig.createDefault();
        exercise.setPlagiarismDetectionConfig(config);

        // when
        PlagiarismDetectionConfigHelper.updateWithTemporaryParameters(exercise, 99, 98, 97);

        // then
        assertThat(exercise.getPlagiarismDetectionConfig()).isNotSameAs(config);
        assertThat(exercise.getPlagiarismDetectionConfig()).extracting(PlagiarismDetectionConfig::getId).isNull();
        assertThat(exercise.getPlagiarismDetectionConfig()).extracting(PlagiarismDetectionConfig::getSimilarityThreshold).isEqualTo(99);
        assertThat(exercise.getPlagiarismDetectionConfig()).extracting(PlagiarismDetectionConfig::getMinimumScore).isEqualTo(98);
        assertThat(exercise.getPlagiarismDetectionConfig()).extracting(PlagiarismDetectionConfig::getMinimumSize).isEqualTo(97);
    }

    @Test
    void shouldValidateDefaultConfig() {
        var exercise = createExerciseWithConfig();

        assertThatCode(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowNullConfigWhenCpcDisabled() {
        var exercise = new ModelingExercise();

        assertThatCode(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSimilarityThresholdBelowRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setSimilarityThreshold(-1);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Similarity threshold must be between 0 and 100");
    }

    @Test
    void shouldRejectSimilarityThresholdAboveRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setSimilarityThreshold(101);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Similarity threshold must be between 0 and 100");
    }

    @Test
    void shouldRejectMinimumScoreOutsideRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setMinimumScore(101);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Minimum score must be between 0 and 100");
    }

    @Test
    void shouldRejectNegativeMinimumSize() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setMinimumSize(-1);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Minimum size must be >= 0");
    }

    @Test
    void shouldRejectResponsePeriodOutsideRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(5);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Response period must be between 7 and 31 days");
    }

    @Test
    void shouldRejectMinimumScoreBelowRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setMinimumScore(-1);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Minimum score must be between 0 and 100");
    }

    @Test
    void shouldRejectResponsePeriodAboveRange() {
        var exercise = createExerciseWithConfig();
        exercise.getPlagiarismDetectionConfig().setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(32);

        assertThatThrownBy(() -> PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exercise, ENTITY_NAME)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Response period must be between 7 and 31 days");
    }
}
