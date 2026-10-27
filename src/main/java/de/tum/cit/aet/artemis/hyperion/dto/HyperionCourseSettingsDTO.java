package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Hyperion settings of one course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Hyperion settings of one course")
public record HyperionCourseSettingsDTO(
        @Schema(description = "Whether the quiz question pool is generated automatically for new lecture material", requiredMode = Schema.RequiredMode.REQUIRED) boolean poolGenerationEnabled,
        @Min(HyperionCourseSettings.MIN_QUESTIONS_PER_LECTURE_UNIT) @Max(HyperionCourseSettings.MAX_QUESTIONS_PER_LECTURE_UNIT) @Schema(description = "Single-choice questions the pool holds per lecture unit and competency", requiredMode = Schema.RequiredMode.REQUIRED) int singleChoiceQuestionsPerLectureUnit,
        @Schema(description = "Whether the pool also holds multiple-choice questions", requiredMode = Schema.RequiredMode.REQUIRED) boolean multipleChoiceEnabled,
        @Min(HyperionCourseSettings.MIN_QUESTIONS_PER_LECTURE_UNIT) @Max(HyperionCourseSettings.MAX_QUESTIONS_PER_LECTURE_UNIT) @Schema(description = "Multiple-choice questions the pool holds per lecture unit and competency while they are enabled", requiredMode = Schema.RequiredMode.REQUIRED) int multipleChoiceQuestionsPerLectureUnit,
        @Min(0) @Max(100) @Schema(description = "Share of each lecture unit's questions generated at the easy difficulty, in percent", requiredMode = Schema.RequiredMode.REQUIRED) int easyPercent,
        @Min(0) @Max(100) @Schema(description = "Share of each lecture unit's questions generated at the medium difficulty, in percent", requiredMode = Schema.RequiredMode.REQUIRED) int mediumPercent,
        @Min(0) @Max(100) @Schema(description = "Share of each lecture unit's questions generated at the hard difficulty, in percent; the three shares add up to 100", requiredMode = Schema.RequiredMode.REQUIRED) int hardPercent) {

    /**
     * Converts the persisted settings to their DTO.
     *
     * @param settings the persisted settings
     * @return the DTO
     */
    public static HyperionCourseSettingsDTO of(HyperionCourseSettings settings) {
        return new HyperionCourseSettingsDTO(settings.poolGenerationEnabled(), settings.singleChoiceQuestionsPerLectureUnit(), settings.multipleChoiceEnabled(),
                settings.multipleChoiceQuestionsPerLectureUnit(), settings.easyPercent(), settings.mediumPercent(), settings.hardPercent());
    }

    /**
     * Converts this DTO to the settings to persist.
     *
     * @return the settings
     */
    public HyperionCourseSettings toSettings() {
        return new HyperionCourseSettings(poolGenerationEnabled, singleChoiceQuestionsPerLectureUnit, multipleChoiceEnabled, multipleChoiceQuestionsPerLectureUnit, easyPercent,
                mediumPercent, hardPercent);
    }

    /**
     * Tells whether the difficulty shares add up to 100 percent.
     *
     * @return whether the shares are complete
     */
    public boolean hasCompleteDifficultyMix() {
        return easyPercent + mediumPercent + hardPercent == 100;
    }
}
