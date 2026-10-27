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
        @Min(HyperionCourseSettings.MIN_QUESTIONS_PER_LECTURE_UNIT) @Max(HyperionCourseSettings.MAX_QUESTIONS_PER_LECTURE_UNIT) @Schema(description = "Questions the pool holds per lecture unit and competency, spread over that unit's cells", requiredMode = Schema.RequiredMode.REQUIRED) int questionsPerLectureUnit) {

    /**
     * Converts the persisted settings to their DTO.
     *
     * @param settings the persisted settings
     * @return the DTO
     */
    public static HyperionCourseSettingsDTO of(HyperionCourseSettings settings) {
        return new HyperionCourseSettingsDTO(settings.poolGenerationEnabled(), settings.questionsPerLectureUnit());
    }

    /**
     * Converts this DTO to the settings to persist.
     *
     * @return the settings
     */
    public HyperionCourseSettings toSettings() {
        return new HyperionCourseSettings(poolGenerationEnabled, questionsPerLectureUnit);
    }
}
