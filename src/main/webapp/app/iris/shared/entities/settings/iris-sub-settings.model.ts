import { BaseEntity } from 'app/shared/model/base-entity';

export enum IrisSubSettingsType {
    CHAT = 'chat', // TODO: Rename to PROGRAMMING_EXERCISE_CHAT
    TEXT_EXERCISE_CHAT = 'text-exercise-chat',
    COURSE_CHAT = 'course-chat',
    LECTURE_INGESTION = 'lecture-ingestion',
    LECTURE = 'lecture-chat',
    COMPETENCY_GENERATION = 'competency-generation',
    FAQ_INGESTION = 'faq-ingestion',
    TUTOR_SUGGESTION = 'tutor-suggestion',
}

export enum IrisEventType {
    BUILD_FAILED = 'build_failed',
    PROGRESS_STALLED = 'progress_stalled',
}

export abstract class IrisSubSettings implements BaseEntity {
    id?: number;
    type: IrisSubSettingsType;
    enabled = false;
    allowedVariants?: string[];
    selectedVariant?: string;
    enabledForCategories?: string[];
    disabledProactiveEvents?: IrisEventType[];
}

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

export class IrisLectureChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.LECTURE;
    rateLimit?: number;
    rateLimitTimeframeHours?: number;
}

export class IrisCourseChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COURSE_CHAT;
    rateLimit?: number;
    rateLimitTimeframeHours?: number;
}

export class IrisLectureIngestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.LECTURE_INGESTION;
    autoIngestOnLectureAttachmentUpload: boolean;
}

export class IrisFaqIngestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.FAQ_INGESTION;
    autoIngestOnFaqCreation: boolean;
}

export class IrisCompetencyGenerationSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COMPETENCY_GENERATION;
}

export class IrisTutorSuggestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.TUTOR_SUGGESTION;
}
