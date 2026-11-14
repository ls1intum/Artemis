package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Course settings with the rate limits that apply to the course and the installation defaults.
 *
 * @param courseId                     the course ID
 * @param settings                     the current course settings
 * @param effectiveRateLimit           the effective configuration for this course
 * @param applicationRateLimitDefaults the installation defaults
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettingsWithRateLimitDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long courseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IrisCourseSettingsDTO settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IrisRateLimitConfigurationDTO effectiveRateLimit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IrisRateLimitConfigurationDTO applicationRateLimitDefaults) {
}
