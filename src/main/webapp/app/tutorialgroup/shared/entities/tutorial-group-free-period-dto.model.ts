import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import dayjs from 'dayjs/esm';

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
        start: dto.start ? dayjs.utc(dto.start) : undefined,
        end: dto.end ? dayjs.utc(dto.end) : undefined,
    };
}
