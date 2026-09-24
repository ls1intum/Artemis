import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupConfigurationFreePeriod } from 'app/openapi/model/tutorial-group-configuration-free-period';
import dayjs from 'dayjs/esm';

export function fromTutorialGroupFreePeriodDTO(dto: TutorialGroupConfigurationFreePeriod): TutorialGroupFreePeriod {
    return {
        id: dto.id,
        start: dayjs.utc(dto.start),
        end: dayjs.utc(dto.end),
        reason: dto.reason,
    };
}
