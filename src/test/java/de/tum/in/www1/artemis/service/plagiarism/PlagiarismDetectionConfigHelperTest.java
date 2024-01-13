package de.tum.in.www1.artemis.service.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;

class PlagiarismDetectionConfigHelperTest {

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
}
