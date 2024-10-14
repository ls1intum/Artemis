import { IrisVariant } from 'app/entities/iris/settings/iris-variant';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisLectureIngestionSubSettings,
    IrisTextExerciseChatSubSettings,
} from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisGlobalSettings } from 'app/entities/iris/settings/iris-settings.model';

export function mockSettings() {
    const mockChatSettings = new IrisChatSubSettings();
    mockChatSettings.id = 1;
    mockChatSettings.enabled = true;
    const mockTextExerciseChatSettings = new IrisTextExerciseChatSubSettings();
    mockTextExerciseChatSettings.id = 13;
    mockTextExerciseChatSettings.enabled = true;
    const mockLectureIngestionSettings = new IrisLectureIngestionSubSettings();
    mockLectureIngestionSettings.id = 7;
    mockLectureIngestionSettings.enabled = true;
    mockLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = true;
    const mockCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
    mockCompetencyGenerationSettings.id = 5;
    mockCompetencyGenerationSettings.enabled = false;
    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    irisSettings.irisChatSettings = mockChatSettings;
    irisSettings.irisTextExerciseChatSettings = mockTextExerciseChatSettings;
    irisSettings.irisCompetencyGenerationSettings = mockCompetencyGenerationSettings;
    irisSettings.irisLectureIngestionSettings = mockLectureIngestionSettings;
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
