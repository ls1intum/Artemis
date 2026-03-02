import { convertDateFromClient } from 'app/shared/util/date.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

export interface TutorialGroupFreePeriodDTO {
    id?: number;
    start: string;
    end: string;
    reason?: string;
    tutorialGroupConfigurationId: number;
}

export const entityToTutorialGroupFreePeriodDTO = (entity?: TutorialGroupFreePeriod): TutorialGroupFreePeriodDTO | undefined => {
    if (!entity) {
        return undefined;
    }
    return {
        id: entity.id,
        start: convertDateFromClient(entity.start)!,
        end: convertDateFromClient(entity.end)!,
        reason: entity.reason,
        tutorialGroupConfigurationId: entity.tutorialGroupConfiguration!.id!,
    };
};
