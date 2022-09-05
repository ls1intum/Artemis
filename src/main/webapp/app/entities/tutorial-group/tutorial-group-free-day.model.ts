import { BaseEntity } from 'app/shared/model/base-entity';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';

export class TutorialGroupFreeDay implements BaseEntity {
    public id?: number;
    public tutorialGroupConfiguration?: TutorialGroupsConfiguration;
    public date?: dayjs.Dayjs;
    public reason?: string;
}
