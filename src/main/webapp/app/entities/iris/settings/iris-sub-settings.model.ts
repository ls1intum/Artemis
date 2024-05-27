import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';

export enum IrisSubSettingsType {
    CHAT = 'chat',
    HESTIA = 'hestia',
    COMPETENCY_GENERATION = 'competency-generation',
    LECTURE_INGESTION = 'ingestion',
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
    type = IrisSubSettingsType.LECTURE_INGESTIION;
}

export class IrisHestiaSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.HESTIA;
    template?: IrisTemplate;
}

export class IrisCompetencyGenerationSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.COMPETENCY_GENERATION;
    template?: IrisTemplate;
}
