import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisEventSettings } from 'app/entities/iris/settings/iris-event-settings.model';

export enum IrisSubSettingsType {
    CHAT = 'chat',
    HESTIA = 'hestia',
    COMPETENCY_GENERATION = 'competency-generation',
    LECTURE_INGESTION = 'lecture-ingestion',
    PROACTIVITY = 'proactivity',
}

export abstract class IrisSubSettings implements BaseEntity {
    id?: number;
    type: IrisSubSettingsType;
    enabled = false;
    allowedModels?: string[];
    preferredModel?: string;
}

export class IrisChatSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.CHAT;
    template?: IrisTemplate;
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

export class IrisHestiaSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.HESTIA;
    template?: IrisTemplate;
}

export class IrisCompetencyGenerationSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COMPETENCY_GENERATION;
    template?: IrisTemplate;
}
