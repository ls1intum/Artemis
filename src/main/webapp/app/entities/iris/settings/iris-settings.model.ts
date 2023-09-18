import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';

export class IrisSettings implements BaseEntity {
    id?: number;
    irisChatSettings?: IrisSubSettings;
    irisHestiaSettings?: IrisSubSettings;
    global = false;
}
