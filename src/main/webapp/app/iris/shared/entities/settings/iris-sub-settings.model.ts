import { BaseEntity } from 'app/shared/model/base-entity';

export enum IrisSubSettingsType {
    PROGRAMMING_EXERCISE_CHAT = 'programming-exercise-chat',
    TEXT_EXERCISE_CHAT = 'text-exercise-chat',
    COURSE_CHAT = 'course-chat',
    LECTURE_INGESTION = 'lecture-ingestion',
    LECTURE = 'lecture-chat',
    COMPETENCY_GENERATION = 'competency-generation',
    FAQ_INGESTION = 'faq-ingestion',
    TUTOR_SUGGESTION = 'tutor-suggestion',
    ALL = 'all',
}

export enum IrisEventType {
    BUILD_FAILED = 'build_failed',
    PROGRESS_STALLED = 'progress_stalled',
}

export abstract class IrisSubSettings implements BaseEntity {
    id?: number;
    type: IrisSubSettingsType;
    enabled = true;
    allowedVariants?: string[];
    selectedVariant?: string;
    enabledForCategories?: string[];
    disabledProactiveEvents?: IrisEventType[];
    customInstructions?: string;
}

export class IrisProgrammingExerciseChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT;
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
    autoIngestOnLectureAttachmentUpload: boolean = true;
}

export class IrisFaqIngestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.FAQ_INGESTION;
    autoIngestOnFaqCreation: boolean = true;
}

export class IrisCompetencyGenerationSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COMPETENCY_GENERATION;
}

export class IrisTutorSuggestionSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.TUTOR_SUGGESTION;
}
