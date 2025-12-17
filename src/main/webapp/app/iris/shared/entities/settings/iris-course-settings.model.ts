/**
 * New simplified course-level Iris settings models matching the server DTOs.
 * These replace the legacy three-tier (Global → Course → Exercise) × 8-feature system.
 */

/**
 * Pipeline variant selection for Iris (admin-only configuration).
 * Matches the server enum serialization as lowercase strings via @JsonValue.
 */
export const IRIS_PIPELINE_VARIANTS = ['default', 'advanced'] as const;
export type IrisPipelineVariant = (typeof IRIS_PIPELINE_VARIANTS)[number];

/**
 * Rate limit configuration with optional per-course overrides.
 * null/undefined values indicate "use application defaults" or "unlimited".
 */
export interface IrisRateLimitConfiguration {
    requests?: number;
    timeframeHours?: number;
}

/**
 * Core settings payload stored in the course_iris_settings JSON column.
 * This is the editable portion of the settings.
 * Note: rateLimit is optional - null/undefined means "use application defaults",
 * while an empty object {} means "explicitly unlimited".
 */
export interface IrisCourseSettingsDTO {
    enabled: boolean;
    customInstructions?: string;
    variant: IrisPipelineVariant;
    rateLimit?: IrisRateLimitConfiguration;
}

/**
 * Complete course settings response from the server API.
 * Includes both the editable settings and the computed effective rate limits.
 */
export interface IrisCourseSettingsWithRateLimitDTO {
    courseId: number;
    settings: IrisCourseSettingsDTO;
    effectiveRateLimit: IrisRateLimitConfiguration;
    applicationRateLimitDefaults: IrisRateLimitConfiguration;
}

/**
 * Helper to create an empty rate limit configuration.
 */
export function createEmptyRateLimit(): IrisRateLimitConfiguration {
    return {};
}

/**
 * Helper to create default course settings.
 */
export function createDefaultCourseSettings(): IrisCourseSettingsDTO {
    return {
        enabled: true,
        variant: 'default',
    };
}
