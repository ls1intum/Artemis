package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;

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
    void applyToExercise_shouldPreserveExistingConfigWhenDtoIsNull() {
        // given: exercise with an existing config
        var exercise = new ModelingExercise();
        var existingConfig = PlagiarismDetectionConfig.createDefault();
        existingConfig.setId(42L);
        exercise.setPlagiarismDetectionConfig(existingConfig);

        // when: a null DTO is applied
        PlagiarismDetectionConfigHelper.applyToExercise(exercise, null);

        // then: the existing config is left untouched (same instance, same identity)
        assertThat(exercise.getPlagiarismDetectionConfig()).isSameAs(existingConfig);
        assertThat(exercise.getPlagiarismDetectionConfig().getId()).isEqualTo(42L);
    }

    @Test
    void applyToExercise_shouldUpdateExistingConfigInPlaceRetainingIdentity() {
        // given: exercise with an existing, persisted config
        var exercise = new ModelingExercise();
        var existingConfig = PlagiarismDetectionConfig.createDefault();
        existingConfig.setId(7L);
        existingConfig.setSimilarityThreshold(30);
        exercise.setPlagiarismDetectionConfig(existingConfig);

        // when: a non-null DTO with new values is applied
        var dto = new PlagiarismDetectionConfigDTO(null, true, true, 14, 70, 8, 12);
        PlagiarismDetectionConfigHelper.applyToExercise(exercise, dto);

        // then: the same managed instance is mutated in place (identity and id preserved), no new object attached
        assertThat(exercise.getPlagiarismDetectionConfig()).isSameAs(existingConfig);
        assertThat(exercise.getPlagiarismDetectionConfig().getId()).isEqualTo(7L);
        assertThat(existingConfig.getSimilarityThreshold()).isEqualTo(70);
        assertThat(existingConfig.getMinimumScore()).isEqualTo(8);
        assertThat(existingConfig.getMinimumSize()).isEqualTo(12);
        assertThat(existingConfig.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(existingConfig.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isTrue();
        assertThat(existingConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(14);
    }

    @Test
    void applyToExercise_shouldCreateAndAttachConfigWhenNoneExists() {
        // given: exercise without a config
        var exercise = new ModelingExercise();

        // when: a non-null DTO is applied
        var dto = new PlagiarismDetectionConfigDTO(null, false, false, 10, 55, 3, 9);
        PlagiarismDetectionConfigHelper.applyToExercise(exercise, dto);

        // then: a new (transient) config carrying the DTO values is attached
        var attachedConfig = exercise.getPlagiarismDetectionConfig();
        assertThat(attachedConfig).isNotNull();
        assertThat(attachedConfig.getId()).isNull();
        assertThat(attachedConfig.getSimilarityThreshold()).isEqualTo(55);
        assertThat(attachedConfig.getMinimumScore()).isEqualTo(3);
        assertThat(attachedConfig.getMinimumSize()).isEqualTo(9);
        assertThat(attachedConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(10);
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
