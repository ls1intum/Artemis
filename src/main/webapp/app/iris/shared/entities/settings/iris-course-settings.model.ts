/**
 * New simplified course-level Iris settings models matching the backend DTOs.
 * These replace the legacy three-tier (Global → Course → Exercise) × 8-feature system.
 */

/**
 * Pipeline variant selection for Iris (admin-only configuration).
 * Matches the backend enum serialization as plain strings.
 */
export type IrisPipelineVariant = 'DEFAULT' | 'ADVANCED';

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
 */
export interface IrisCourseSettingsDTO {
    enabled: boolean;
    customInstructions?: string;
    variant: IrisPipelineVariant;
    rateLimit: IrisRateLimitConfiguration;
}

/**
 * Complete course settings response from the backend API.
 * Includes both the editable settings and the computed effective rate limits.
 */
export interface CourseIrisSettingsDTO {
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
        variant: 'DEFAULT',
        rateLimit: createEmptyRateLimit(),
    };
}
