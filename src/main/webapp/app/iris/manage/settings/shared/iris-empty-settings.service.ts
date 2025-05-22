import { Injectable } from '@angular/core';
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
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';

@Injectable({
    providedIn: 'root',
})
export class IrisEmptySettingsService {
    fillEmptyIrisSubSettings(irisSettings?: IrisSettings): IrisSettings | undefined {
        if (!irisSettings) {
            return;
        }
        if (!irisSettings.irisChatSettings) {
            irisSettings.irisChatSettings = new IrisChatSubSettings();
        }
        if (!irisSettings.irisTextExerciseChatSettings) {
            irisSettings.irisTextExerciseChatSettings = new IrisTextExerciseChatSubSettings();
        }
        if (!irisSettings.irisLectureChatSettings) {
            irisSettings.irisLectureChatSettings = new IrisLectureChatSubSettings();
        }
        if (!irisSettings.irisCourseChatSettings) {
            irisSettings.irisCourseChatSettings = new IrisCourseChatSubSettings();
        }
        if (!irisSettings.irisLectureIngestionSettings) {
            irisSettings.irisLectureIngestionSettings = new IrisLectureIngestionSubSettings();
        }
        if (!irisSettings.irisCompetencyGenerationSettings) {
            irisSettings.irisCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
        }
        if (!irisSettings.irisFaqIngestionSettings) {
            irisSettings.irisFaqIngestionSettings = new IrisFaqIngestionSubSettings();
        }
        if (!irisSettings.irisTutorSuggestionSettings) {
            irisSettings.irisTutorSuggestionSettings = new IrisTutorSuggestionSubSettings();
        }
        return irisSettings;
    }
}
