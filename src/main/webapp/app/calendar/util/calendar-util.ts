import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import { CalendarEvent } from '../entities/calendar-event.model';

dayjs.extend(isoWeek);

export function nameOf(day: Dayjs): string {
    return day.format('dd');
}

export function isCurrentDay(day: Dayjs): boolean {
    return day.isSame(dayjs(), 'day');
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

export function getWeeksOfMonthFor(dayInMonth: Dayjs): Dayjs[][] {
    const start = dayInMonth.startOf('month').startOf('isoWeek');
    const end = dayInMonth.endOf('month').endOf('isoWeek');
    const weeks: Dayjs[][] = [];
    let date = start;
    while (date.isBefore(end)) {
        const week: Dayjs[] = [];
        for (let i = 0; i < 7; i++) {
            week.push(date.clone());
            date = date.add(1, 'day');
        }
        weeks.push(week);
    }
    return weeks;
}

export function getWeekOf(day: Dayjs): Dayjs[] {
    const start = day.startOf('isoWeek');
    const week: Dayjs[] = [];
    let date = start;
    for (let i = 0; i < 7; i++) {
        week.push(date.clone());
        date = date.add(1, 'day');
    }
    return week;
}

// assumes that all days of the week are really in the same isoWeek
export function daysAreInWeek(days: Dayjs[], week: Dayjs[]): boolean {
    return days.every((day) => day.isSame(week[0], 'isoWeek'));
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
