import { IrisVariant } from 'app/iris/shared/entities/settings/iris-variant';
import {
    IrisProgrammingExerciseChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisCourseChatSubSettings,
    IrisFaqIngestionSubSettings,
    IrisLectureChatSubSettings,
    IrisLectureIngestionSubSettings,
    IrisTextExerciseChatSubSettings,
    IrisTutorSuggestionSubSettings,
} from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { IrisGlobalSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { CourseIrisSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

export function mockSettings() {
    const mockChatSettings = new IrisProgrammingExerciseChatSubSettings();
    mockChatSettings.id = 1;
    mockChatSettings.enabled = true;
    mockChatSettings.disabledProactiveEvents = [];
    const mockTextExerciseChatSettings = new IrisTextExerciseChatSubSettings();
    mockTextExerciseChatSettings.id = 13;
    mockTextExerciseChatSettings.enabled = true;
    const mockCourseChatSettings = new IrisCourseChatSubSettings();
    mockCourseChatSettings.id = 3;
    mockCourseChatSettings.enabled = true;
    const mockLectureIngestionSettings = new IrisLectureIngestionSubSettings();
    mockLectureIngestionSettings.id = 7;
    mockLectureIngestionSettings.enabled = true;
    mockLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = true;
    const mockCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
    mockCompetencyGenerationSettings.id = 5;
    mockCompetencyGenerationSettings.enabled = false;

    const mockIrisLectureSettings = new IrisLectureChatSubSettings();
    mockIrisLectureSettings.id = 42;
    mockIrisLectureSettings.enabled = true;

    const mockIrisTutorSuggestionSettings = new IrisTutorSuggestionSubSettings();
    mockIrisTutorSuggestionSettings.id = 99;
    mockIrisTutorSuggestionSettings.enabled = true;

    const mockFaqIngestionSettings = new IrisFaqIngestionSubSettings();
    mockFaqIngestionSettings.id = 8;
    mockFaqIngestionSettings.enabled = true;
    mockFaqIngestionSettings.autoIngestOnFaqCreation = true;

    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    irisSettings.irisProgrammingExerciseChatSettings = mockChatSettings;
    irisSettings.irisTextExerciseChatSettings = mockTextExerciseChatSettings;
    irisSettings.irisCourseChatSettings = mockCourseChatSettings;
    irisSettings.irisCompetencyGenerationSettings = mockCompetencyGenerationSettings;
    irisSettings.irisLectureIngestionSettings = mockLectureIngestionSettings;
    irisSettings.irisLectureChatSettings = mockIrisLectureSettings;
    irisSettings.irisTutorSuggestionSettings = mockIrisTutorSuggestionSettings;
    irisSettings.irisFaqIngestionSettings = mockFaqIngestionSettings;
    return irisSettings;
}

export function mockEmptySettings() {
    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    return irisSettings;
}

export function mockVariants() {
    return [
        {
            id: '1',
            name: 'Model 1',
            description: 'Model 1 Description',
        },
        {
            id: '2',
            name: 'Model 2',
            description: 'Model 2 Description',
        },
    ] as IrisVariant[];
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
            variant: { id: 'DEFAULT' },
            rateLimit: { requests: 100, timeframeHours: 24 },
        },
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };
}
