import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/shared/util/date.utils';

export class TutorialGroupFreePeriodDTO {
    public id?: number;
    public start?: string;
    public end?: string;
}

export function toTutorialGroupFreePeriodDTO(entity: TutorialGroupFreePeriod): TutorialGroupFreePeriodDTO {
    return {
        id: entity.id,
        start: convertDateFromClient(entity.start),
        end: convertDateFromClient(entity.end),
    };
}

export function fromTutorialGroupFreePeriodDTO(dto: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriod {
    return {
        id: dto.id,
        start: convertDateStringFromServer(dto.start),
        end: convertDateStringFromServer(dto.end),
    };
}
