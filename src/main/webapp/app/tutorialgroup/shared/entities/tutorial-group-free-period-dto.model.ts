import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-configuration-dto.model';

export class TutorialGroupFreePeriodDTO {
    public id?: number;
    public tutorialGroupsConfiguration?: TutorialGroupConfigurationDTO;
    public startDate?: Date;
    public endDate?: Date;
    public reason?: string;
}
