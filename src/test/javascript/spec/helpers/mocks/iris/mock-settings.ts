import { CourseIrisSettingsDTO, IrisPipelineVariant } from 'app/iris/shared/entities/settings/iris-course-settings.model';

export function mockVariants(): IrisPipelineVariant[] {
    return ['default', 'advanced'];
}

/**
 * Creates a mock CourseIrisSettingsDTO for the new simplified course-level API
 */
export function mockCourseSettings(courseId: number = 1, enabled: boolean = true): CourseIrisSettingsDTO {
    return {
        courseId,
        settings: {
            enabled,
            customInstructions: 'Test instructions',
            variant: 'default',
            rateLimit: { requests: 100, timeframeHours: 24 },
        },
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };
}
