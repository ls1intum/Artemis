import { IrisVariant } from 'app/iris/shared/entities/settings/iris-variant';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisCourseChatSubSettings,
    IrisLectureChatSubSettings,
    IrisLectureIngestionSubSettings,
    IrisTextExerciseChatSubSettings,
} from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { IrisGlobalSettings } from 'app/iris/shared/entities/settings/iris-settings.model';

export function mockSettings() {
    const mockChatSettings = new IrisChatSubSettings();
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

    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    irisSettings.irisChatSettings = mockChatSettings;
    irisSettings.irisTextExerciseChatSettings = mockTextExerciseChatSettings;
    irisSettings.irisCourseChatSettings = mockCourseChatSettings;
    irisSettings.irisCompetencyGenerationSettings = mockCompetencyGenerationSettings;
    irisSettings.irisLectureIngestionSettings = mockLectureIngestionSettings;
    irisSettings.irisLectureChatSettings = mockIrisLectureSettings;
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
