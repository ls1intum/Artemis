import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

/** `YYYY-MM-DD`, the key both the session counts and the calendar grid are indexed by. */
export const DAY_KEY_FORMAT = 'YYYY-MM-DD';

/**
 * A span during which sessions are cancelled, read in the time zone of the course.
 *
 * A free period is stored as two instants, which is enough to express all three shapes the old page asked the reader to
 * choose between up front: a whole day, a run of whole days, and a span within one day. Which of those a holiday is
 * follows from its start and end rather than from a separate kind, so it is derived here instead of being stored.
 */
export interface Holiday {
    readonly period: TutorialGroupFreePeriod;
    /** Start of the span, in the time zone of the course. */
    readonly start: dayjs.Dayjs;
    readonly end: dayjs.Dayjs;
    readonly reason: string;
    /** True when the span covers its days completely, from 00:00 on the first to 23:59 on the last. */
    readonly wholeDay: boolean;
    readonly spansMultipleDays: boolean;
    /** How many calendar days the span touches, counting both ends. */
    readonly dayCount: number;
    /** `HH:mm` in the course's zone. Precomputed because the date pipe would re-read the instant in the reader's. */
    readonly startTime: string;
    readonly endTime: string;
}

/**
 * Reads an instant in the time zone the course keeps its tutorial groups in.
 *
 * Everything on this page is expressed in that zone rather than the reader's: a holiday cancels the sessions that fall
 * on a day in the course's zone, so a reader in another zone must still see the day the sessions are cancelled on.
 */
export function inCourseZone(instant: dayjs.Dayjs, timeZone: string | undefined): dayjs.Dayjs {
    return timeZone ? instant.tz(timeZone) : instant;
}

/** The last minute of a day, which is how the end of a whole-day holiday is stored. */
export function endOfHolidayDay(day: dayjs.Dayjs): dayjs.Dayjs {
    return day.startOf('day').set('hour', 23).set('minute', 59).startOf('minute');
}

/** Whether a span runs from midnight on its first day to 23:59 on its last, rather than being narrowed within a day. */
export function coversWholeDays(start: dayjs.Dayjs, end: dayjs.Dayjs): boolean {
    return start.hour() === 0 && start.minute() === 0 && end.hour() === 23 && end.minute() === 59;
}

/**
 * Projects the free periods of a course into the shape the page renders, ascending by start.
 *
 * Rows without a start or an end are skipped rather than placed at an arbitrary day: they cannot be put on the calendar,
 * and showing them somewhere wrong is worse than leaving them out.
 */
export function toHolidays(periods: readonly TutorialGroupFreePeriod[], timeZone: string | undefined): Holiday[] {
    const holidays: Holiday[] = [];
    for (const period of periods) {
        if (!period.start || !period.end) {
            continue;
        }
        const start = inCourseZone(period.start, timeZone);
        const end = inCourseZone(period.end, timeZone);
        holidays.push({
            period,
            start,
            end,
            reason: period.reason ?? '',
            wholeDay: coversWholeDays(start, end),
            spansMultipleDays: !start.isSame(end, 'day'),
            dayCount: end.startOf('day').diff(start.startOf('day'), 'day') + 1,
            startTime: start.format('HH:mm'),
            endTime: end.format('HH:mm'),
        });
    }
    return holidays.sort((left, right) => left.start.valueOf() - right.start.valueOf());
}

/**
 * Indexes the holidays by every day they touch, so the calendar can look a day up without scanning the list.
 *
 * A holiday spanning several days appears under each of them, which is what puts a two-week break on all of its days
 * while keeping it a single entry in the list beside the calendar.
 */
export function holidaysByDay(holidays: readonly Holiday[]): Map<string, Holiday[]> {
    const byDay = new Map<string, Holiday[]>();
    for (const holiday of holidays) {
        let day = holiday.start.startOf('day');
        const lastDay = holiday.end.startOf('day');
        while (!day.isAfter(lastDay)) {
            const key = day.format(DAY_KEY_FORMAT);
            const existing = byDay.get(key);
            if (existing) {
                existing.push(holiday);
            } else {
                byDay.set(key, [holiday]);
            }
            day = day.add(1, 'day');
        }
    }
    return byDay;
}
