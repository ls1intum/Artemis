import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

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
    return Array.from({ length: 23 }, (_, i) => `${(i + 1).toString().padStart(2, '0')}:00`);
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

export function getEventDescriptor(event: CalendarEvent): string | undefined {
    const eventId = event.id;
    if (event.isTutorialEvent()) {
        return undefined;
    } else if (event.isLectureEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('startDate')) {
            return 'artemisApp.calendar.eventDescriptor.tutorialStart';
        } else {
            return 'artemisApp.calendar.eventDescriptor.tutorialEnd';
        }
    } else if (event.isExamEvent()) {
        if (eventId.endsWith('startAndEndDate')) {
            return undefined;
        } else if (eventId.endsWith('publishResultsDate')) {
            return 'artemisApp.calendar.eventDescriptor.examPublishResults';
        } else if (eventId.endsWith('studentReviewStartDate')) {
            return 'artemisApp.calendar.eventDescriptor.examReviewStart';
        } else {
            return 'artemisApp.calendar.eventDescriptor.examReviewEnd';
        }
    } else {
        if (event.isQuizExerciseEvent()) {
            if (eventId.endsWith('startAndEndDate')) {
                return undefined;
            } else if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventDescriptor.exerciseRelease';
            } else {
                return 'artemisApp.calendar.eventDescriptor.exerciseDue';
            }
        } else {
            if (eventId.endsWith('releaseDate')) {
                return 'artemisApp.calendar.eventDescriptor.exerciseRelease';
            } else if (eventId.endsWith('startDate')) {
                return 'artemisApp.calendar.eventDescriptor.exerciseStart';
            } else if (eventId.endsWith('dueDate')) {
                return 'artemisApp.calendar.eventDescriptor.exerciseDue';
            } else {
                return 'artemisApp.calendar.eventDescriptor.exerciseAssessmentDue';
            }
        }
    }
}

export function getTimeString(timestamp: Dayjs): string {
    return timestamp.format('HH:mm');
}
