import { IrisModel } from 'app/entities/iris/settings/iris-model';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisHestiaSubSettings,
    IrisLectureIngestionSubSettings,
} from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisGlobalSettings } from 'app/entities/iris/settings/iris-settings.model';

export function mockSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const mockChatSettings = new IrisChatSubSettings();
    mockChatSettings.id = 1;
    mockChatSettings.template = mockTemplate;
    mockChatSettings.enabled = true;
    const mockLectureIngestionSettings = new IrisLectureIngestionSubSettings();
    mockLectureIngestionSettings.id = 7;
    mockLectureIngestionSettings.enabled = true;
    mockLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = true;
    const mockHestiaSettings = new IrisHestiaSubSettings();
    mockHestiaSettings.id = 2;
    mockHestiaSettings.template = mockTemplate;
    mockHestiaSettings.enabled = true;
    const mockCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
    mockCompetencyGenerationSettings.id = 5;
    mockCompetencyGenerationSettings.enabled = false;
    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    irisSettings.irisChatSettings = mockChatSettings;
    irisSettings.irisHestiaSettings = mockHestiaSettings;
    irisSettings.irisCompetencyGenerationSettings = mockCompetencyGenerationSettings;
    irisSettings.irisLectureIngestionSettings = mockLectureIngestionSettings;
    return irisSettings;
}

export function mockEmptySettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSettings = new IrisGlobalSettings();
    irisSettings.id = 1;
    return irisSettings;
}

export function mockModels() {
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
    ] as IrisModel[];
}
