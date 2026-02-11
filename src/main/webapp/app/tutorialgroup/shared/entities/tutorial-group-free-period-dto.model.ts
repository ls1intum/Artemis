import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import dayjs from 'dayjs/esm';

export class TutorialGroupFreePeriodDTO {
    public id?: number;
    public start?: string;
    public end?: string;
    public reason?: string;
}

export function toTutorialGroupFreePeriodDTO(tutorialGroupFreePeriod: TutorialGroupFreePeriod): TutorialGroupFreePeriodDTO {
    return {
        id: tutorialGroupFreePeriod.id,
        start: convertDateFromClient(tutorialGroupFreePeriod.start),
        end: convertDateFromClient(tutorialGroupFreePeriod.end),
        reason: tutorialGroupFreePeriod.reason,
    };
}

export function fromTutorialGroupFreePeriodDTO(dto: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriod {
    return {
        id: dto.id,
        start: dto.start ? dayjs.utc(dto.start) : undefined,
        end: dto.end ? dayjs.utc(dto.end) : undefined,
        reason: dto.reason,
    };
}
