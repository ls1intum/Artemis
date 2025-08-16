import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/esm/plugin/isoWeek';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { CalendarEvent, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';

dayjs.extend(isoWeek);

export function getDatesInWeekOf(date: Dayjs): Dayjs[] {
    const start = date.startOf('isoWeek');
    const week: Dayjs[] = [];
    let currentDay = start;
    for (let i = 0; i < 7; i++) {
        week.push(currentDay.clone());
        currentDay = currentDay.add(1, 'day');
    }
    return week;
}

export function getWeekDayNameKey(day: Dayjs): string {
    const keys = getWeekDayNameKeys();
    return keys[day.isoWeekday() - 1];
}

export function getHoursOfDay(): string[] {
    const hours = Array.from({ length: 23 }, (_, i) => `${(i + 1).toString().padStart(2, '0')}:00`);
    hours.push('00:00');
    return hours;
}

export function areDatesInSameMonth(firstDay: Dayjs, secondDay: Dayjs): boolean {
    return firstDay.month() === secondDay.month();
}

/**
 * Generates a unique identifier string for a given day or week, intended for use in Angular structural directives like @for.
 *
 * - If a single Dayjs object (representing a day) is passed, returns its formatted date string.
 * - If an array of Dayjs objects (representing a week) is passed, returns the formatted string of the first day of the week.
 *
 * @param {Dayjs | Dayjs[]} dateObject - a single Dayjs object (day) or an array of Dayjs objects (week).
 * @returns {string} A formatted date string (`'YYYY-MM-DD'`) representing the day or start of the week.
 */
export function identify(dateObject: Dayjs | Dayjs[]): string {
    if (dayjs.isDayjs(dateObject)) {
        return dateObject.format('YYYY-MM-DD');
    } else {
        return dateObject[0].format('YYYY-MM-DD');
    }
}

export function getTimeString(timestamp: Dayjs): string {
    return timestamp.format('HH:mm');
}

export function limitToLengthTwo(events: CalendarEvent[]): CalendarEvent[] {
    return events.slice(0, 2);
}

export function range(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
}

const eventTypeIconMap: Record<CalendarEventType, IconProp> = {
    [CalendarEventType.Lecture]: faChalkboardUser,
    [CalendarEventType.Tutorial]: faPersonChalkboard,
    [CalendarEventType.Exam]: faGraduationCap,
    [CalendarEventType.QuizExercise]: faCheckDouble,
    [CalendarEventType.TextExercise]: faFont,
    [CalendarEventType.ModelingExercise]: faDiagramProject,
    [CalendarEventType.ProgrammingExercise]: faKeyboard,
    [CalendarEventType.FileUploadExercise]: faFileArrowUp,
};

export function getIconForEvent(event: CalendarEvent): IconProp {
    return eventTypeIconMap[event.type];
}

export function getWeekDayNameKeys(): string[] {
    return [
        'artemisApp.calendar.mondayShort',
        'artemisApp.calendar.tuesdayShort',
        'artemisApp.calendar.wednesdayShort',
        'artemisApp.calendar.thursdayShort',
        'artemisApp.calendar.fridayShort',
        'artemisApp.calendar.saturdayShort',
        'artemisApp.calendar.sundayShort',
    ];
}

const eventTypeNameKeyMap: Record<CalendarEventType, string> = {
    [CalendarEventType.Lecture]: 'artemisApp.calendar.eventTypeName.lecture',
    [CalendarEventType.Tutorial]: 'artemisApp.calendar.eventTypeName.tutorial',
    [CalendarEventType.Exam]: 'artemisApp.calendar.eventTypeName.exam',
    [CalendarEventType.QuizExercise]: 'artemisApp.calendar.eventTypeName.quiz',
    [CalendarEventType.TextExercise]: 'artemisApp.calendar.eventTypeName.text',
    [CalendarEventType.ModelingExercise]: 'artemisApp.calendar.eventTypeName.modeling',
    [CalendarEventType.ProgrammingExercise]: 'artemisApp.calendar.eventTypeName.programming',
    [CalendarEventType.FileUploadExercise]: 'artemisApp.calendar.eventTypeName.fileUpload',
};

export function getEventTypeNameKey(event: CalendarEvent): string {
    return eventTypeNameKeyMap[event.type];
}

type EventTypeAndSubtype = `${CalendarEventType}:${CalendarEventSubtype}`;

const eventSubtypeNameKeyMap: Partial<Record<EventTypeAndSubtype, string>> = {
    [`${CalendarEventType.Lecture}:${CalendarEventSubtype.StartDate}`]: 'artemisApp.calendar.eventSubtypeName.lectureStart',
    [`${CalendarEventType.Lecture}:${CalendarEventSubtype.EndDate}`]: 'artemisApp.calendar.eventSubtypeName.lectureEnd',

    [`${CalendarEventType.Exam}:${CalendarEventSubtype.PublishResultsDate}`]: 'artemisApp.calendar.eventSubtypeName.examPublishResults',
    [`${CalendarEventType.Exam}:${CalendarEventSubtype.StudentReviewStartDate}`]: 'artemisApp.calendar.eventSubtypeName.examReviewStart',
    [`${CalendarEventType.Exam}:${CalendarEventSubtype.StudentReviewEndDate}`]: 'artemisApp.calendar.eventSubtypeName.examReviewEnd',

    [`${CalendarEventType.QuizExercise}:${CalendarEventSubtype.ReleaseDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseRelease',
    [`${CalendarEventType.QuizExercise}:${CalendarEventSubtype.DueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseDue',

    [`${CalendarEventType.TextExercise}:${CalendarEventSubtype.ReleaseDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseRelease',
    [`${CalendarEventType.TextExercise}:${CalendarEventSubtype.StartDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseStart',
    [`${CalendarEventType.TextExercise}:${CalendarEventSubtype.DueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseDue',
    [`${CalendarEventType.TextExercise}:${CalendarEventSubtype.AssessmentDueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue',

    [`${CalendarEventType.ModelingExercise}:${CalendarEventSubtype.ReleaseDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseRelease',
    [`${CalendarEventType.ModelingExercise}:${CalendarEventSubtype.StartDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseStart',
    [`${CalendarEventType.ModelingExercise}:${CalendarEventSubtype.DueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseDue',
    [`${CalendarEventType.ModelingExercise}:${CalendarEventSubtype.AssessmentDueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue',

    [`${CalendarEventType.ProgrammingExercise}:${CalendarEventSubtype.ReleaseDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseRelease',
    [`${CalendarEventType.ProgrammingExercise}:${CalendarEventSubtype.StartDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseStart',
    [`${CalendarEventType.ProgrammingExercise}:${CalendarEventSubtype.DueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseDue',
    [`${CalendarEventType.ProgrammingExercise}:${CalendarEventSubtype.AssessmentDueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue',

    [`${CalendarEventType.FileUploadExercise}:${CalendarEventSubtype.ReleaseDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseRelease',
    [`${CalendarEventType.FileUploadExercise}:${CalendarEventSubtype.StartDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseStart',
    [`${CalendarEventType.FileUploadExercise}:${CalendarEventSubtype.DueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseDue',
    [`${CalendarEventType.FileUploadExercise}:${CalendarEventSubtype.AssessmentDueDate}`]: 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue',
};

export function getEventSubtypeNameKey(event: CalendarEvent): string | undefined {
    const key = `${event.type}:${event.subtype}` as EventTypeAndSubtype;
    return eventSubtypeNameKeyMap[key];
}

export enum CalendarEventFilterOption {
    ExamEvents = 'examEvents',
    LectureEvents = 'lectureEvents',
    TutorialEvents = 'tutorialEvents',
    ExerciseEvents = 'exerciseEvents',
}

const filterOptionNameKeyMap: Record<CalendarEventFilterOption, string> = {
    exerciseEvents: 'artemisApp.calendar.filterOption.exercises',
    lectureEvents: 'artemisApp.calendar.filterOption.lectures',
    tutorialEvents: 'artemisApp.calendar.filterOption.tutorials',
    examEvents: 'artemisApp.calendar.filterOption.exams',
};

export function getFilterOptionNameKey(option: CalendarEventFilterOption): string {
    return filterOptionNameKeyMap[option];
}
