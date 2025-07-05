import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faCheckDouble, faDiagramProject, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { CalendarEvent, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';

dayjs.extend(isoWeek);

export function getWeekDayNameKey(day: Dayjs): string {
    const keys = getWeekDayNameKeys();
    return keys[day.isoWeekday() - 1];
}

export function getHoursOfDay(): string[] {
    const hours = Array.from({ length: 23 }, (_, i) => `${(i + 1).toString().padStart(2, '0')}:00`);
    hours.push('00:00');
    return hours;
}

export function areDaysInSameMonth(firstDay: Dayjs, secondDay: Dayjs): boolean {
    return firstDay.month() === secondDay.month();
}

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

export function getIconForEvent(event: CalendarEvent): IconProp {
    if (event.isExerciseEvent()) {
        if (event.isTextExerciseEvent()) {
            return faFont;
        } else if (event.isModelingExerciseEvent()) {
            return faDiagramProject;
        } else if (event.isQuizExerciseEvent()) {
            return faCheckDouble;
        } else if (event.isProgrammingExercise()) {
            return faKeyboard;
        } else {
            return faFileArrowUp;
        }
    } else if (event.isLectureEvent()) {
        return faChalkboardUser;
    } else if (event.isTutorialEvent()) {
        return faPersonChalkboard;
    } else {
        return faGraduationCap;
    }
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
    [CalendarEventType.Lecture]: 'artemisApp.calendar.eventName.lecture',
    [CalendarEventType.Tutorial]: 'artemisApp.calendar.eventName.tutorial',
    [CalendarEventType.Exam]: 'artemisApp.calendar.eventName.exam',
    [CalendarEventType.QuizExercise]: 'artemisApp.calendar.eventName.quiz',
    [CalendarEventType.TextExercise]: 'artemisApp.calendar.eventName.text',
    [CalendarEventType.ModelingExercise]: 'artemisApp.calendar.eventName.modeling',
    [CalendarEventType.ProgrammingExercise]: 'artemisApp.calendar.eventName.programming',
    [CalendarEventType.FileUploadExercise]: 'artemisApp.calendar.eventName.fileUpload',
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

export type CalendarEventFilterOption = 'examEvents' | 'lectureEvents' | 'tutorialEvents' | 'exerciseEvents';

const filterOptionNameKeyMap: Record<CalendarEventFilterOption, string> = {
    exerciseEvents: 'artemisApp.calendar.exerciseFilterOption',
    lectureEvents: 'artemisApp.calendar.lectureFilterOption',
    tutorialEvents: 'artemisApp.calendar.tutorialFilterOption',
    examEvents: 'artemisApp.calendar.examFilterOption',
};

export function getFilterOptionNameKey(option: CalendarEventFilterOption): string {
    return filterOptionNameKeyMap[option];
}
