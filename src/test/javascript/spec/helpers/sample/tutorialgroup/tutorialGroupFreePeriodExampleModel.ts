import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

export const generateExampleTutorialGroupFreePeriod = ({
    id = 1,
    start = dayjs.utc('2021-01-01T00:00:00'),
    end = dayjs.utc('2021-01-01T23:59:59'),
    reason = 'Example Reason',
}: TutorialGroupFreePeriod) => {
    const examplePeriod = new TutorialGroupFreePeriod();
    examplePeriod.id = id;
    // we get utc from the server --> will be converted to time zone of configuration
    examplePeriod.start = start;
    examplePeriod.end = end;
    examplePeriod.reason = reason;
    return examplePeriod;
};
