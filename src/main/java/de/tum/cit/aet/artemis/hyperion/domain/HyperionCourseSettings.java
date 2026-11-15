package de.tum.cit.aet.artemis.hyperion.domain;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON object persisted for the Hyperion settings of one course.
 *
 * @param poolGenerationEnabled                 whether the quiz question pool is generated automatically for new lecture material
 * @param singleChoiceQuestionsPerLectureUnit   single-choice questions the pool holds per lecture unit and competency
 * @param multipleChoiceEnabled                 whether the pool also holds multiple-choice questions
 * @param multipleChoiceQuestionsPerLectureUnit multiple-choice questions the pool holds per lecture unit and competency while they are enabled
 * @param multipleChoiceMinCorrectOptions       the fewest answer options a multiple-choice question marks correct
 * @param multipleChoiceMaxCorrectOptions       the most answer options a multiple-choice question marks correct; not below the fewest
 * @param easyPercent                           share of each lecture unit's questions generated at the easy difficulty, in percent
 * @param mediumPercent                         share of each lecture unit's questions generated at the medium difficulty, in percent
 * @param hardPercent                           share of each lecture unit's questions generated at the hard difficulty, in percent; the three shares add up to 100
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HyperionCourseSettings(boolean poolGenerationEnabled, int singleChoiceQuestionsPerLectureUnit, boolean multipleChoiceEnabled,
        int multipleChoiceQuestionsPerLectureUnit, int multipleChoiceMinCorrectOptions, int multipleChoiceMaxCorrectOptions, int easyPercent, int mediumPercent, int hardPercent)
        implements Serializable {

    public static final int DEFAULT_QUESTIONS_PER_LECTURE_UNIT = 12;

    public static final int MIN_QUESTIONS_PER_LECTURE_UNIT = 1;

    public static final int MAX_QUESTIONS_PER_LECTURE_UNIT = 60;

    /** The fewest answer options a multiple-choice question can mark correct. */
    public static final int MIN_CORRECT_OPTIONS = 1;

    /** The most answer options a multiple-choice question can mark correct: all of its options. */
    public static final int MAX_CORRECT_OPTIONS = 4;

    public static final int DEFAULT_EASY_PERCENT = 20;

    public static final int DEFAULT_MEDIUM_PERCENT = 50;

    public static final int DEFAULT_HARD_PERCENT = 30;

    private static final HyperionCourseSettings DEFAULT = new HyperionCourseSettings(false, DEFAULT_QUESTIONS_PER_LECTURE_UNIT, false, DEFAULT_QUESTIONS_PER_LECTURE_UNIT,
            MIN_CORRECT_OPTIONS, MAX_CORRECT_OPTIONS, DEFAULT_EASY_PERCENT, DEFAULT_MEDIUM_PERCENT, DEFAULT_HARD_PERCENT);

    /**
     * Creates settings from a persisted payload, substituting the defaults for fields the payload does not carry. A payload that carries
     * {@code questionsPerLectureUnit} instead of the single-choice count is read as that count, the difficulty shares are read only when
     * the payload carries all three, and the correct-option range only when the payload carries both bounds within 1 to 4 and in order.
     *
     * @param poolGenerationEnabled                 the stored flag, or {@code null} when the payload predates it
     * @param singleChoiceQuestionsPerLectureUnit   the stored single-choice count, or {@code null} when the payload predates it
     * @param questionsPerLectureUnit               the count of a payload that predates the question types, or {@code null}
     * @param multipleChoiceEnabled                 the stored flag, or {@code null} when the payload predates it
     * @param multipleChoiceQuestionsPerLectureUnit the stored multiple-choice count, or {@code null} when the payload predates it
     * @param multipleChoiceMinCorrectOptions       the stored lower bound, or {@code null} when the payload predates it
     * @param multipleChoiceMaxCorrectOptions       the stored upper bound, or {@code null} when the payload predates it
     * @param easyPercent                           the stored share, or {@code null} when the payload predates it
     * @param mediumPercent                         the stored share, or {@code null} when the payload predates it
     * @param hardPercent                           the stored share, or {@code null} when the payload predates it
     */
    @JsonCreator
    public HyperionCourseSettings(@JsonProperty("poolGenerationEnabled") @Nullable Boolean poolGenerationEnabled,
            @JsonProperty("singleChoiceQuestionsPerLectureUnit") @Nullable Integer singleChoiceQuestionsPerLectureUnit,
            @JsonProperty("questionsPerLectureUnit") @Nullable Integer questionsPerLectureUnit, @JsonProperty("multipleChoiceEnabled") @Nullable Boolean multipleChoiceEnabled,
            @JsonProperty("multipleChoiceQuestionsPerLectureUnit") @Nullable Integer multipleChoiceQuestionsPerLectureUnit,
            @JsonProperty("multipleChoiceMinCorrectOptions") @Nullable Integer multipleChoiceMinCorrectOptions,
            @JsonProperty("multipleChoiceMaxCorrectOptions") @Nullable Integer multipleChoiceMaxCorrectOptions, @JsonProperty("easyPercent") @Nullable Integer easyPercent,
            @JsonProperty("mediumPercent") @Nullable Integer mediumPercent, @JsonProperty("hardPercent") @Nullable Integer hardPercent) {
        this(Boolean.TRUE.equals(poolGenerationEnabled), orDefault(singleChoiceQuestionsPerLectureUnit != null ? singleChoiceQuestionsPerLectureUnit : questionsPerLectureUnit),
                Boolean.TRUE.equals(multipleChoiceEnabled), orDefault(multipleChoiceQuestionsPerLectureUnit),
                hasCorrectOptionRange(multipleChoiceMinCorrectOptions, multipleChoiceMaxCorrectOptions) ? multipleChoiceMinCorrectOptions : MIN_CORRECT_OPTIONS,
                hasCorrectOptionRange(multipleChoiceMinCorrectOptions, multipleChoiceMaxCorrectOptions) ? multipleChoiceMaxCorrectOptions : MAX_CORRECT_OPTIONS,
                hasMix(easyPercent, mediumPercent, hardPercent) ? easyPercent : DEFAULT_EASY_PERCENT,
                hasMix(easyPercent, mediumPercent, hardPercent) ? mediumPercent : DEFAULT_MEDIUM_PERCENT,
                hasMix(easyPercent, mediumPercent, hardPercent) ? hardPercent : DEFAULT_HARD_PERCENT);
    }

    public static HyperionCourseSettings defaultSettings() {
        return DEFAULT;
    }

    /**
     * Returns the share of each lecture unit's questions generated at a difficulty.
     *
     * @param difficulty the difficulty
     * @return the share in percent
     */
    public int percentOf(QuizQuestionDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> easyPercent;
            case MEDIUM -> mediumPercent;
            case HARD -> hardPercent;
        };
    }

    private static int orDefault(@Nullable Integer count) {
        return count == null ? DEFAULT_QUESTIONS_PER_LECTURE_UNIT : count;
    }

    private static boolean hasMix(@Nullable Integer easyPercent, @Nullable Integer mediumPercent, @Nullable Integer hardPercent) {
        return easyPercent != null && mediumPercent != null && hardPercent != null;
    }

    private static boolean hasCorrectOptionRange(@Nullable Integer min, @Nullable Integer max) {
        return min != null && max != null && min >= MIN_CORRECT_OPTIONS && min <= max && max <= MAX_CORRECT_OPTIONS;
    }
}
