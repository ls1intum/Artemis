import { IrisCourseSettingsWithRateLimitDTO, IrisPipelineVariant } from 'app/iris/shared/entities/settings/iris-course-settings.model';

export function mockVariants(): IrisPipelineVariant[] {
    return ['default', 'advanced'];
}

/**
 * Creates a mock IrisCourseSettingsWithRateLimitDTO for the new simplified course-level API
 */
export function mockCourseSettings(courseId: number = 1, enabled: boolean = true): IrisCourseSettingsWithRateLimitDTO {
    return {
        courseId,
        settings: {
            enabled,
            promptingModeEnabled: true,
            customInstructions: 'Test instructions',
            variant: 'default',
            promptingModeSettings: { minQuestions: 3, maxQuestions: 5, timeLimitQuestion: 20, timeLimitInClass: 15 },
            rateLimit: { requests: 100, timeframeHours: 24 },
        },
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };
}
