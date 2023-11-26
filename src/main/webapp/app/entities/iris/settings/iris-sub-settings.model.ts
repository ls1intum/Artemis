import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';

export enum IrisSubSettingsType {
    CHAT = 'chat',
    HESTIA = 'hestia',
    CODE_EDITOR = 'code-editor',
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

export class IrisHestiaSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.HESTIA;
    template?: IrisTemplate;
}

export class IrisCodeEditorSubSettings extends IrisSubSettings {
    type = IrisSubSettingsType.CODE_EDITOR;
    chatTemplate?: IrisTemplate;
    problemStatementGenerationTemplate?: IrisTemplate;
    templateRepoGenerationTemplate?: IrisTemplate;
    solutionRepoGenerationTemplate?: IrisTemplate;
    testRepoGenerationTemplate?: IrisTemplate;
}
