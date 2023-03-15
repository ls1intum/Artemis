import dayjs from 'dayjs/esm';

import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class TutorialGroupFreePeriod implements BaseEntity {
    public id?: number;
    public tutorialGroupConfiguration?: TutorialGroupsConfiguration;
    public start?: dayjs.Dayjs;
    public end?: dayjs.Dayjs;
    public reason?: string;
}
