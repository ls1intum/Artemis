import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { CalendarEvent } from '../entities/calendar-event.model';

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

export function limitToLengthThree(events: CalendarEvent[]): CalendarEvent[] {
    return events.slice(0, 3);
}
