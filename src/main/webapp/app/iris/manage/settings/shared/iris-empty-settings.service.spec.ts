import { TestBed } from '@angular/core/testing';

import { IrisEmptySettingsService } from './iris-empty-settings.service';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisCourseChatSubSettings,
    IrisFaqIngestionSubSettings,
    IrisLectureChatSubSettings,
    IrisLectureIngestionSubSettings,
    IrisTextExerciseChatSubSettings,
    IrisTutorSuggestionSubSettings,
} from 'app/iris/shared/entities/settings/iris-sub-settings.model';

describe('IrisEmptySettingsService', () => {
    let service: IrisEmptySettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(IrisEmptySettingsService);
    });
    describe('fillEmptyIrisSubSettings', () => {
        it('should do nothing if irisSettings is undefined', () => {
            expect(service.fillEmptyIrisSubSettings(undefined)).toBeUndefined();
        });

        it('should create each sub-setting if not defined', () => {
            const result = service.fillEmptyIrisSubSettings({} as IrisSettings);
            expect(result!.irisChatSettings).toBeInstanceOf(IrisChatSubSettings);
            expect(result!.irisTextExerciseChatSettings).toBeInstanceOf(IrisTextExerciseChatSubSettings);
            expect(result!.irisLectureChatSettings).toBeInstanceOf(IrisLectureChatSubSettings);
            expect(result!.irisCourseChatSettings).toBeInstanceOf(IrisCourseChatSubSettings);
            expect(result!.irisLectureIngestionSettings).toBeInstanceOf(IrisLectureIngestionSubSettings);
            expect(result!.irisCompetencyGenerationSettings).toBeInstanceOf(IrisCompetencyGenerationSubSettings);
            expect(result!.irisFaqIngestionSettings).toBeInstanceOf(IrisFaqIngestionSubSettings);
            expect(result!.irisTutorSuggestionSettings).toBeInstanceOf(IrisTutorSuggestionSubSettings);
        });

        it('should preserve existing sub-settings and only create missing ones', () => {
            const existingChatSettings = new IrisChatSubSettings();
            existingChatSettings.enabled = true;
            existingChatSettings.selectedVariant = 'existingVariant';

            const existingLectureIngestionSettings = new IrisLectureIngestionSubSettings();
            existingLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = true;

            const existingSettings = {
                irisChatSettings: existingChatSettings,
                irisLectureIngestionSettings: existingLectureIngestionSettings,
            } as IrisSettings;

            const result = service.fillEmptyIrisSubSettings(existingSettings);

            expect(result!.irisChatSettings).toBe(existingChatSettings);
            expect(result!.irisChatSettings!.enabled).toBeTrue();
            expect(result!.irisChatSettings!.selectedVariant).toBe('existingVariant');

            expect(result!.irisLectureIngestionSettings).toBe(existingLectureIngestionSettings);
            expect(result!.irisLectureIngestionSettings!.autoIngestOnLectureAttachmentUpload).toBeTrue();

            expect(result!.irisTextExerciseChatSettings).toBeInstanceOf(IrisTextExerciseChatSubSettings);
            expect(result!.irisLectureChatSettings).toBeInstanceOf(IrisLectureChatSubSettings);
            expect(result!.irisCourseChatSettings).toBeInstanceOf(IrisCourseChatSubSettings);
            expect(result!.irisCompetencyGenerationSettings).toBeInstanceOf(IrisCompetencyGenerationSubSettings);
            expect(result!.irisFaqIngestionSettings).toBeInstanceOf(IrisFaqIngestionSubSettings);
            expect(result!.irisTutorSuggestionSettings).toBeInstanceOf(IrisTutorSuggestionSubSettings);
        });
    });
});
