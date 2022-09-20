import dayjs from 'dayjs/esm';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionDTO } from 'app/course/tutorial-groups/services/tutorial-group-session.service';

export const generateExampleTutorialGroupSession = () => {
    const exampleSession = new TutorialGroupSession();
    exampleSession.id = 3;
    // we get utc from the server --> will be converted to time zone of configuration
    exampleSession.start = dayjs.utc('2021-01-01T10:00:00');
    exampleSession.end = dayjs.utc('2021-01-01T11:00:00');
    exampleSession.location = 'Room 1';
    return exampleSession;
};

export const tutorialGroupSessionToTutorialGroupSessionFormData = (entity: TutorialGroupSession, tz: string): TutorialGroupSessionFormData => {
    return {
        date: entity.start!.tz(tz).toDate(),
        startTime: entity.start!.tz(tz).format('HH:mm:ss'),
        endTime: entity.end!.tz(tz).format('HH:mm:ss'),
        location: entity.location,
    };
};

export const formDataToTutorialGroupSessionDTO = (formData: TutorialGroupSessionFormData): TutorialGroupSessionDTO => {
    return {
        date: formData.date,
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location,
    };
};
