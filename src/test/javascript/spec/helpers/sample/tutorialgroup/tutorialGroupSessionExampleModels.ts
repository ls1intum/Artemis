import dayjs from 'dayjs/esm';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';

export const generateExampleTutorialGroupSession = ({
    id = 3,
    start = dayjs.utc('2021-01-01T10:00:00'),
    end = dayjs.utc('2021-01-01T11:00:00'),
    location = 'Room 1',
    status = TutorialGroupSessionStatus.ACTIVE,
}: TutorialGroupSession) => {
    const exampleSession = new TutorialGroupSession();
    exampleSession.id = id;
    // we get utc from the server --> will be converted to time zone of configuration
    exampleSession.start = start;
    exampleSession.end = end;
    exampleSession.location = location;
    exampleSession.status = status;

    return exampleSession;
};
