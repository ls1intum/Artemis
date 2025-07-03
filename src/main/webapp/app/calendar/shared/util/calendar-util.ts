import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardTeacher, faCheckDouble, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';

dayjs.extend(isoWeek);

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

export function getWeekDayNameKey(day: Dayjs): string {
    const keys = getWeekDayNameKeys();
    return keys[day.isoWeekday() - 1];
}

export function getHoursOfDay(): string[] {
    const hours = Array.from({ length: 23 }, (_, i) => `${(i + 1).toString().padStart(2, '0')}:00`);
    hours.push('00:00');
    return hours;
}

export function range(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
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

export function limitToLengthTwo(events: CalendarEvent[]): CalendarEvent[] {
    return events.slice(0, 2);
}

export function getEventSubtypeDescriptor(event: CalendarEvent): string | undefined {
    const eventId = event.id;
    if (event.isTutorialEvent()) {
        return undefined;
    } else if (event.isLectureEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('startDate')) {
            return 'artemisApp.calendar.eventSubtypeDescriptor.tutorialStart';
        } else {
            return 'artemisApp.calendar.eventSubtypeDescriptor.tutorialEnd';
        }
    } else if (event.isExamEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('publishResultsDate')) {
            return 'artemisApp.calendar.eventSubtypeDescriptor.examPublishResults';
        } else if (eventId.endsWith('studentReviewStartDate')) {
            return 'artemisApp.calendar.eventSubtypeDescriptor.examReviewStart';
        } else {
            return 'artemisApp.calendar.eventSubtypeDescriptor.examReviewEnd';
        }
    } else {
        if (event.isQuizExerciseEvent()) {
            if (eventId.endsWith('startAndEndDate')) {
                return undefined;
            } else if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseRelease';
            } else {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseDue';
            }
        } else {
            if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseRelease';
            } else if (eventId.endsWith('startDate')) {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseStart';
            } else if (eventId.endsWith('dueDate')) {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseDue';
            } else {
                return 'artemisApp.calendar.eventSubtypeDescriptor.exerciseAssessmentDue';
            }
        }
    }
}

export function getTimeString(timestamp: Dayjs): string {
    return timestamp.format('HH:mm');
}

export function getIconForEvent(event: CalendarEvent): IconProp {
    if (event.isExerciseEvent()) {
        if (event.isTextExerciseEvent()) {
            return faFont;
        } else if (event.isModelingExerciseEvent()) {
            return faProjectDiagram;
        } else if (event.isQuizExerciseEvent()) {
            return faCheckDouble;
        } else if (event.isProgrammingExercise()) {
            return faKeyboard;
        } else {
            return faFileArrowUp;
        }
    } else if (event.isLectureEvent()) {
        return faChalkboardTeacher;
    } else if (event.isTutorialEvent()) {
        return faPersonChalkboard;
    } else {
        return faGraduationCap;
    }
}

export function getExerciseDescriptor(event: CalendarEvent): string {
    if (event.isExerciseEvent()) {
        if (event.isProgrammingExercise()) {
            return 'artemisApp.calendar.eventDescriptor.programming';
        } else if (event.isTextExerciseEvent()) {
            return 'artemisApp.calendar.eventDescriptor.text';
        } else if (event.isModelingExerciseEvent()) {
            return 'artemisApp.calendar.eventDescriptor.modeling';
        } else if (event.isQuizExerciseEvent()) {
            return 'artemisApp.calendar.eventDescriptor.quiz';
        } else {
            return 'artemisApp.calendar.eventDescriptor.fileUpload';
        }
    } else if (event.isLectureEvent()) {
        return 'artemisApp.calendar.eventDescriptor.lecture';
    } else if (event.isTutorialEvent()) {
        return 'artemisApp.calendar.eventDescriptor.tutorial';
    } else {
        return 'artemisApp.calendar.eventDescriptor.exam';
    }
}

export type CalendarEventFilterOption = 'examEvents' | 'lectureEvents' | 'tutorialEvents' | 'exerciseEvents';
