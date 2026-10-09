package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pins the copy constructor of the plagiarism detection configuration: the settings are copied, the identity (row id,
 * exercise and its key) is not. All settings are primitives, so the copy cannot share state with its source. It also pins
 * the redaction of the settings for students and the value semantics of the configuration.
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

    private static PlagiarismDetectionConfig storedConfig(long id) {
        PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();
        config.setId(id);
        return config;
    }

    @Test
    void filteringSensitiveInformationRedactsTheSettingsAStudentMustNotSee() {
        PlagiarismDetectionConfig config = attachedSourceWithNonDefaultSettings();
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);

        config.filterSensitiveInformation();

        assertThat(config.isContinuousPlagiarismControlEnabled()).isFalse();
        assertThat(config.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(config.getSimilarityThreshold()).isEqualTo(-1);
        assertThat(config.getMinimumScore()).isEqualTo(-1);
        assertThat(config.getMinimumSize()).isEqualTo(-1);
        assertThat(config.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).as("the response period is not sensitive").isEqualTo(14);
    }

    @Test
    void createDefaultHasTheDocumentedDefaults() {
        PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();

        assertThat(config.isContinuousPlagiarismControlEnabled()).isFalse();
        assertThat(config.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isFalse();
        assertThat(config.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(7);
        assertThat(config.getSimilarityThreshold()).isEqualTo(90);
        assertThat(config.getMinimumScore()).isZero();
        assertThat(config.getMinimumSize()).isEqualTo(50);
    }

    @Test
    void configurationsAreEqualWhenRowAndEverySettingAreEqual() {
        PlagiarismDetectionConfig config = storedConfig(1L);

        assertThat(config).isEqualTo(config);
        assertThat(config).isEqualTo(storedConfig(1L)).hasSameHashCodeAs(storedConfig(1L));
        assertThat(config).isNotEqualTo(null);
        assertThat(config).isNotEqualTo("not a configuration");
        assertThat(config).as("another row").isNotEqualTo(storedConfig(2L));
        assertThat(PlagiarismDetectionConfig.createDefault()).as("a row that is not stored has no identity to compare").isNotEqualTo(PlagiarismDetectionConfig.createDefault());
    }

    @Test
    void configurationsWithTheSameRowDifferWhenAnySettingDiffers() {
        PlagiarismDetectionConfig base = storedConfig(1L);

        PlagiarismDetectionConfig enabled = storedConfig(1L);
        enabled.setContinuousPlagiarismControlEnabled(true);
        PlagiarismDetectionConfig postDueDate = storedConfig(1L);
        postDueDate.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        PlagiarismDetectionConfig period = storedConfig(1L);
        period.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(8);
        PlagiarismDetectionConfig threshold = storedConfig(1L);
        threshold.setSimilarityThreshold(91);
        PlagiarismDetectionConfig score = storedConfig(1L);
        score.setMinimumScore(1);
        PlagiarismDetectionConfig size = storedConfig(1L);
        size.setMinimumSize(51);

        assertThat(List.of(enabled, postDueDate, period, threshold, score, size)).allSatisfy(changed -> assertThat(base).isNotEqualTo(changed));
    }

    @Test
    void toStringListsEverySetting() {
        PlagiarismDetectionConfig config = attachedSourceWithNonDefaultSettings();

        assertThat(config.toString()).contains("continuousPlagiarismControlEnabled=true", "continuousPlagiarismControlPostDueDateChecksEnabled=false",
                "continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod=14", "similarityThreshold=77", "minimumScore=33", "minimumSize=55");
    }
}
