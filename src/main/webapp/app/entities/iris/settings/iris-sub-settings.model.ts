import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';

export enum IrisModel {
    GPT35 = 'GPT35',
}

export class IrisSubSettings implements BaseEntity {
    id?: number;
    enabled = false;
    template?: IrisTemplate;
    preferredModel?: IrisModel;
}
