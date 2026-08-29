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
 * Instructional support level for Iris at the course level.
 * Matches the server enum serialization as lowercase strings via @JsonValue
 * (see IrisSupportLevel.java). The backend defaults an absent/unknown value to MODERATE.
 */
export const IRIS_SUPPORT_LEVELS = ['low', 'moderate', 'high'] as const;
export type IrisSupportLevel = (typeof IRIS_SUPPORT_LEVELS)[number];

/**
 * Maps each support level to its discrete slider position (0 / 50 / 100).
 */
export const SUPPORT_LEVEL_SLIDER_VALUES: Record<IrisSupportLevel, number> = {
    low: 0,
    moderate: 50,
    high: 100,
};

/**
 * Reverse mapping from a discrete slider position back to the support level.
 */
export const SLIDER_VALUE_TO_SUPPORT_LEVEL: Record<number, IrisSupportLevel> = {
    0: 'low',
    50: 'moderate',
    100: 'high',
};

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
    // Optional: absent means "use server default" (MODERATE), mirroring the @Nullable backend field.
    supportLevel?: IrisSupportLevel;
    rateLimit?: IrisRateLimitConfiguration;
    /**
     * Admin-only A/B toggle (spec §13): when true, Iris proactively detects struggle and offers help. Off by default;
     * a course that was never opted in loads as `false` (a row predating this field has no key, so it deserializes to
     * off server-side). Optional so an older/omitted payload without the key is still accepted.
     */
    proactiveStruggleEnabled?: boolean;
    /**
     * Admin-only: may Artemis' OWN build-triggered proactive Iris events (build_failed / progress_stalled) fire for
     * this course? Three states, and the third one matters. `undefined` means no admin ever decided: a settings row
     * written before this field existed has no key, and a save from a client that does not edit the field omits it.
     * The server merges an omitted value from what is stored, so neither case silently flips a course, and an absent
     * value reads as ON, which is what every course did before the field existed.
     *
     * Normalize it with `?? true`, never `!!` — unlike `proactiveStruggleEnabled`, absent does NOT mean off here.
     */
    legacyBuildTriggersEnabled?: boolean | null;
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
        supportLevel: 'moderate',
        proactiveStruggleEnabled: false,
        // Deliberately absent, not `true`: this resets the general-tab fields, and spelling the admin
        // field out here would make every such reset send a decision the admin never made.
    };
}
