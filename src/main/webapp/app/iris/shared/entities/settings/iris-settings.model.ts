import { BaseEntity } from 'app/shared/model/base-entity';
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

export enum IrisSettingsType {
    GLOBAL = 'global',
    COURSE = 'course',
    EXERCISE = 'exercise',
}

export abstract class IrisSettings implements BaseEntity {
    id?: number;
    type: IrisSettingsType;
    irisChatSettings?: IrisChatSubSettings;
    irisTextExerciseChatSettings?: IrisTextExerciseChatSubSettings;
    irisLectureChatSettings?: IrisLectureChatSubSettings;
    irisCourseChatSettings?: IrisCourseChatSubSettings;
    irisLectureIngestionSettings?: IrisLectureIngestionSubSettings;
    irisCompetencyGenerationSettings?: IrisCompetencyGenerationSubSettings;
    irisFaqIngestionSettings?: IrisFaqIngestionSubSettings;
    irisTutorSuggestionSettings?: IrisTutorSuggestionSubSettings;
}

export class IrisGlobalSettings implements IrisSettings {
    id?: number;
    type = IrisSettingsType.GLOBAL;
    irisChatSettings?: IrisChatSubSettings;
    irisTextExerciseChatSettings?: IrisTextExerciseChatSubSettings;
    irisLectureChatSettings?: IrisLectureChatSubSettings;
    irisCourseChatSettings?: IrisCourseChatSubSettings;
    irisLectureIngestionSettings?: IrisLectureIngestionSubSettings;
    irisCompetencyGenerationSettings?: IrisCompetencyGenerationSubSettings;
    irisFaqIngestionSettings?: IrisFaqIngestionSubSettings;
    irisTutorSuggestionSettings?: IrisTutorSuggestionSubSettings;
}

export class IrisCourseSettings implements IrisSettings {
    id?: number;
    type = IrisSettingsType.COURSE;
    courseId?: number;
    irisChatSettings?: IrisChatSubSettings;
    irisTextExerciseChatSettings?: IrisTextExerciseChatSubSettings;
    irisLectureChatSettings?: IrisLectureChatSubSettings;
    irisCourseChatSettings?: IrisCourseChatSubSettings;
    irisLectureIngestionSettings?: IrisLectureIngestionSubSettings;
    irisCompetencyGenerationSettings?: IrisCompetencyGenerationSubSettings;
    irisFaqIngestionSettings?: IrisFaqIngestionSubSettings;
    irisTutorSuggestionSettings?: IrisTutorSuggestionSubSettings;
}

export class IrisExerciseSettings implements IrisSettings {
    id?: number;
    type = IrisSettingsType.EXERCISE;
    exerciseId?: number;
    irisChatSettings?: IrisChatSubSettings;
    irisTextExerciseChatSettings?: IrisTextExerciseChatSubSettings;
}
