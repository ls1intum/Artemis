import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisModel } from 'app/entities/iris/settings/iris-model';

export class IrisSubSettings implements BaseEntity {
    id?: number;
    enabled = false;
    template?: IrisTemplate;
    preferredModel?: string;
}
