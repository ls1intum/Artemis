package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pins the copy constructor of the plagiarism detection configuration: the settings are copied, the identity (row id,
 * exercise and its key) is not. All settings are primitives, so the copy cannot share state with its source.
 */
class PlagiarismDetectionConfigTest {

    private static PlagiarismDetectionConfig attachedSourceWithNonDefaultSettings() {
        PlagiarismDetectionConfig source = new PlagiarismDetectionConfig();
        source.setContinuousPlagiarismControlEnabled(true);
        // the two flags differ, so assigning one to the other is noticed
        source.setContinuousPlagiarismControlPostDueDateChecksEnabled(false);
        source.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(14);
        source.setSimilarityThreshold(77);
        source.setMinimumScore(33);
        source.setMinimumSize(55);
        source.setId(5L);
        TextExercise exercise = new TextExercise();
        exercise.setId(123L);
        source.setExercise(exercise);
        // the key is read from the database column, so it is set directly
        ReflectionTestUtils.setField(source, "exerciseId", 123L);
        return source;
    }

    @Test
    void copyTakesEverySettingOfTheSource() {
        PlagiarismDetectionConfig copy = new PlagiarismDetectionConfig(attachedSourceWithNonDefaultSettings());

        assertThat(copy.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(copy.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(copy.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(14);
        assertThat(copy.getSimilarityThreshold()).isEqualTo(77);
        assertThat(copy.getMinimumScore()).isEqualTo(33);
        assertThat(copy.getMinimumSize()).isEqualTo(55);
    }

    @Test
    void copyKeepsTheFlagsApartInTheOtherCombinationToo() {
        PlagiarismDetectionConfig source = new PlagiarismDetectionConfig();
        source.setContinuousPlagiarismControlEnabled(false);
        source.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);

        PlagiarismDetectionConfig copy = new PlagiarismDetectionConfig(source);

        assertThat(copy.isContinuousPlagiarismControlEnabled()).isFalse();
        assertThat(copy.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isTrue();
    }

    @Test
    void copyBelongsToNoExerciseAndHasNoRowIdentity() {
        PlagiarismDetectionConfig source = attachedSourceWithNonDefaultSettings();
        assertThat(source.getExercise()).isNotNull();
        assertThat(source.getExerciseId()).isEqualTo(123L);

        PlagiarismDetectionConfig copy = new PlagiarismDetectionConfig(source);

        assertThat(copy.getExercise()).as("the exercise identifies the source's exercise").isNull();
        assertThat(copy.getExerciseId()).as("the key identifies the source's exercise").isNull();
        assertThat(copy.getId()).as("the copy is a new row").isNull();
        assertThat(copy).isNotSameAs(source).isNotEqualTo(source);
        assertThat(source.getExercise()).as("copying leaves the source attached").isNotNull();
        assertThat(source.getExerciseId()).isEqualTo(123L);
        assertThat(source.getId()).isEqualTo(5L);
    }

    @Test
    void copyOfTheDefaultsKeepsTheDefaults() {
        PlagiarismDetectionConfig copy = new PlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());

        assertThat(copy.isContinuousPlagiarismControlEnabled()).isFalse();
        assertThat(copy.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(copy.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(7);
        assertThat(copy.getSimilarityThreshold()).isEqualTo(90);
        assertThat(copy.getMinimumScore()).isZero();
        assertThat(copy.getMinimumSize()).isEqualTo(50);
        assertThat(copy.getExercise()).isNull();
        assertThat(copy.getExerciseId()).isNull();
    }
}
