import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisEventSettings } from 'app/entities/iris/settings/iris-event-settings.model';

export enum IrisSubSettingsType {
    TEXT_EXERCISE_CHAT = 'text-exercise-chat',
    CHAT = 'chat', // TODO: Split into PROGRAMMING_EXERCISE_CHAT and COURSE_CHAT
    COMPETENCY_GENERATION = 'competency-generation',
    LECTURE_INGESTION = 'lecture-ingestion',
    PROACTIVITY = 'proactivity',
}

export abstract class IrisSubSettings implements BaseEntity {
    id?: number;
    type: IrisSubSettingsType;
    enabled = false;
    allowedVariants?: string[];
    selectedVariant?: string;
}

// TODO: Split into ProgrammingExerciseChatSubSettings and CourseChatSubSettings
// TODO: Each feature should probably get its own rate limit instead of sharing one
export class IrisChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.CHAT;
    rateLimit?: number;
    rateLimitTimeframeHours?: number;
}

export class IrisTextExerciseChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.TEXT_EXERCISE_CHAT;
    rateLimit?: number;
    rateLimitTimeframeHours?: number;
}

export class IrisLectureIngestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.LECTURE_INGESTION;
    autoIngestOnLectureAttachmentUpload: boolean;
}

export class IrisProactivitySubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.PROACTIVITY;
    eventSettings: IrisEventSettings[];
}

export class IrisCompetencyGenerationSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COMPETENCY_GENERATION;
}
