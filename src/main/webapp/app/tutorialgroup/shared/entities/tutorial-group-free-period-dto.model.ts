import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-configuration-dto.model';
import dayjs from 'dayjs/esm';

export class TutorialGroupFreePeriodDTO {
    public id?: number;
    public tutorialGroupsConfiguration?: TutorialGroupConfigurationDTO;
    public start?: dayjs.Dayjs;
    public end?: dayjs.Dayjs;
    public reason?: string;
}
