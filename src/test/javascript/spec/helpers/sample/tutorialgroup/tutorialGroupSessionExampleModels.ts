import dayjs from 'dayjs/esm';
import {
    TutorialGroupSession,
    TutorialGroupSessionDTO,
    TutorialGroupSessionRequestDTO,
    TutorialGroupSessionStatus,
} from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';

/**
 * Generates an example {@link TutorialGroupSession} entity.
 *
 * This helper is primarily used in unit tests to create
 * consistent and predictable entity instances.
 *
 * Dates are initialized in UTC to simulate server responses.
 *
 * @param id the session id
 * @param start the start timestamp (Dayjs, UTC)
 * @param end the end timestamp (Dayjs, UTC)
 * @param location the session location
 * @param status the session status
 * @returns a fully initialized TutorialGroupSession entity
 */
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

/**
 * Generates an example {@link TutorialGroupSessionDTO}.
 *
 * This helper is used in frontend tests where the UI layer
 * operates purely on DTO objects.
 *
 * All date values are ISO 8601 strings.
 *
 * @param overrides optional properties to override defaults
 * @returns a fully initialized TutorialGroupSessionDTO
 */
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

/**
 * Converts a {@link TutorialGroupSessionDTO} into form data
 * used by the session creation/edit dialog.
 *
 * @param dto the session DTO
 * @param tz the time zone to apply
 * @returns form data suitable for the session form component
 */
export const tutorialGroupSessionDtoToFormData = (dto: TutorialGroupSessionDTO, tz: string): TutorialGroupSessionFormData => {
    const start = dto.startDate ? dayjs.tz(dto.startDate, tz) : undefined;
    const end = dto.endDate ? dayjs.tz(dto.endDate, tz) : undefined;

    return {
        date: start?.toDate(),
        startTime: start?.format('HH:mm'),
        endTime: end?.format('HH:mm'),
        location: dto.location,
    };
};

/**
 * Converts session form data into a {@link TutorialGroupSessionRequestDTO}
 * used when creating or updating a session.
 *
 * @param formData the form input data
 * @throws Error if required fields are missing
 * @returns request DTO for backend submission
 */
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
