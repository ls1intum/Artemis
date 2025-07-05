import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardTeacher, faCheckDouble, faFileArrowUp, faFont, faGraduationCap, faKeyboard, faPersonChalkboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';

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

export function getEventNameKey(event: CalendarEvent): string {
    if (event.isExerciseEvent()) {
        if (event.isProgrammingExercise()) {
            return 'artemisApp.calendar.eventName.programming';
        } else if (event.isTextExerciseEvent()) {
            return 'artemisApp.calendar.eventName.text';
        } else if (event.isModelingExerciseEvent()) {
            return 'artemisApp.calendar.eventName.modeling';
        } else if (event.isQuizExerciseEvent()) {
            return 'artemisApp.calendar.eventName.quiz';
        } else {
            return 'artemisApp.calendar.eventName.fileUpload';
        }
    } else if (event.isLectureEvent()) {
        return 'artemisApp.calendar.eventName.lecture';
    } else if (event.isTutorialEvent()) {
        return 'artemisApp.calendar.eventName.tutorial';
    } else {
        return 'artemisApp.calendar.eventName.exam';
    }
}

export function getEventSubtypeNameKey(event: CalendarEvent): string | undefined {
    const eventId = event.id;
    if (event.isTutorialEvent()) {
        return undefined;
    } else if (event.isLectureEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('startDate')) {
            return 'artemisApp.calendar.eventSubtypeName.tutorialStart';
        } else {
            return 'artemisApp.calendar.eventSubtypeName.tutorialEnd';
        }
    } else if (event.isExamEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('publishResultsDate')) {
            return 'artemisApp.calendar.eventSubtypeName.examPublishResults';
        } else if (eventId.endsWith('studentReviewStartDate')) {
            return 'artemisApp.calendar.eventSubtypeName.examReviewStart';
        } else {
            return 'artemisApp.calendar.eventSubtypeName.examReviewEnd';
        }
    } else {
        if (event.isQuizExerciseEvent()) {
            if (eventId.endsWith('startAndEndDate')) {
                return undefined;
            } else if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventSubtypeName.exerciseRelease';
            } else {
                return 'artemisApp.calendar.eventSubtypeName.exerciseDue';
            }
        } else {
            if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventSubtypeName.exerciseRelease';
            } else if (eventId.endsWith('startDate')) {
                return 'artemisApp.calendar.eventSubtypeName.exerciseStart';
            } else if (eventId.endsWith('dueDate')) {
                return 'artemisApp.calendar.eventSubtypeName.exerciseDue';
            } else {
                return 'artemisApp.calendar.eventSubtypeName.exerciseAssessmentDue';
            }
        }
    }
}

export function getFilterOptionNameKey(option: CalendarEventFilterOption): string {
    switch (option) {
        case 'exerciseEvents':
            return 'artemisApp.calendar.exerciseFilterOption';
        case 'lectureEvents':
            return 'artemisApp.calendar.lectureFilterOption';
        case 'tutorialEvents':
            return 'artemisApp.calendar.tutorialFilterOption';
        case 'examEvents':
            return 'artemisApp.calendar.examFilterOption';
    }
}

export type CalendarEventFilterOption = 'examEvents' | 'lectureEvents' | 'tutorialEvents' | 'exerciseEvents';
