import dayjs from 'dayjs/esm';
import {
    TutorialGroupSession,
    TutorialGroupSessionDTO,
    TutorialGroupSessionRequestDTO,
    TutorialGroupSessionStatus,
} from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';

export const generateExampleTutorialGroupSession = ({
    id = 3,
    start = dayjs.utc('2021-01-01T10:00:00'),
    end = dayjs.utc('2021-01-01T11:00:00'),
    location = 'Room 1',
    status = TutorialGroupSessionStatus.ACTIVE,
}: TutorialGroupSession) => {
    const exampleSession = new TutorialGroupSession();
    exampleSession.id = id;
    // we get utc from the server --> will be converted to the time zone of configuration
    exampleSession.start = start;
    exampleSession.end = end;
    exampleSession.location = location;
    exampleSession.status = status;

    return exampleSession;
};

export const generateExampleTutorialGroupSessionDTO = ({
    id = 3,
    startDate = '2021-01-01T10:00:00Z',
    endDate = '2021-01-01T11:00:00Z',
    location = 'Room 1',
    status = TutorialGroupSessionStatus.ACTIVE,
    statusExplanation,
    attendanceCount,
    schedule,
    freePeriod,
}: Partial<TutorialGroupSessionDTO> = {}): TutorialGroupSessionDTO => {
    return {
        id,
        startDate,
        endDate,
        location,
        status,
        statusExplanation,
        attendanceCount,
        schedule,
        freePeriod,
    };
};

export const tutorialGroupSessionDtoToFormData = (dto: TutorialGroupSessionDTO, tz: string): TutorialGroupSessionFormData => {
    const start = dayjs(dto.startDate).tz(tz);
    const end = dayjs(dto.endDate).tz(tz);

    return {
        date: start.toDate(),
        startTime: start.format('HH:mm'),
        endTime: end.format('HH:mm'),
        location: dto.location,
    };
};

export const formDataToTutorialGroupSessionDTO = (formData: TutorialGroupSessionFormData): TutorialGroupSessionRequestDTO => {
    if (!formData.date || !formData.startTime || !formData.endTime) {
        throw new Error('Date, startTime and endTime are required');
    }

    return {
        date: formData.date.toISOString().split('T')[0],
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location!,
    };
};
