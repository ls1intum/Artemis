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
    // we get utc from the server --> will be converted to time zone of configuration
    exampleSession.start = start;
    exampleSession.end = end;
    exampleSession.location = location;
    exampleSession.status = status;

    return exampleSession;
};

export const generateExampleTutorialGroupSessionDTO = ({
    id = 3,
    startDate = dayjs.utc('2021-01-01T10:00:00'),
    endDate = dayjs.utc('2021-01-01T11:00:00'),
    location = 'Room 1',
    isCancelled = false,
    statusExplanation,
    attendanceCount,
    schedule,
    freePeriod,
}: TutorialGroupSessionDTO = {}) => {
    const dto = new TutorialGroupSessionDTO();
    dto.id = id;
    dto.startDate = startDate;
    dto.endDate = endDate;
    dto.location = location;
    dto.isCancelled = isCancelled;
    dto.statusExplanation = statusExplanation;
    dto.attendanceCount = attendanceCount;
    dto.schedule = schedule;
    dto.freePeriod = freePeriod;
    return dto;
};

export const tutorialGroupSessionDtoToFormData = (dto: TutorialGroupSessionDTO, tz: string): TutorialGroupSessionFormData => {
    return {
        date: dto.startDate!.tz(tz).toDate(),
        startTime: dto.startDate!.tz(tz).format('HH:mm:ss'),
        endTime: dto.endDate!.tz(tz).format('HH:mm:ss'),
        location: dto.location,
    };
};

export const formDataToTutorialGroupSessionDTO = (formData: TutorialGroupSessionFormData): TutorialGroupSessionRequestDTO => {
    const dto = new TutorialGroupSessionRequestDTO();
    dto.date = formData.date;
    dto.startTime = formData.startTime;
    dto.endTime = formData.endTime;
    dto.location = formData.location;
    return dto;
};
