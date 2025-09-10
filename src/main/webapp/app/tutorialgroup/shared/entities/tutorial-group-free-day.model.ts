import { BaseEntity } from 'app/shared/model/base-entity';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import dayjs from 'dayjs/esm';

export class TutorialGroupFreePeriod implements BaseEntity {
    public id?: number;
    public tutorialGroupConfiguration?: TutorialGroupsConfiguration;
    public start?: dayjs.Dayjs;
    public end?: dayjs.Dayjs;
    public reason?: string;
}
