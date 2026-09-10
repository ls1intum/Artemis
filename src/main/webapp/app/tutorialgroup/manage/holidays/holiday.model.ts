import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

/** `YYYY-MM-DD`, the key both the session counts and the calendar grid are indexed by. */
export const DAY_KEY_FORMAT = 'YYYY-MM-DD';

/**
 * The bounds a holiday covering a whole day is stored between.
 *
 * A free period holds two instants and nothing that says "all day", so covering a day means starting at its first
 * minute and ending at its last. Reading and writing that has to agree on those two, hence the names.
 */
const FIRST_HOUR_OF_DAY = 0;
const FIRST_MINUTE_OF_HOUR = 0;
const LAST_HOUR_OF_DAY = 23;
const LAST_MINUTE_OF_HOUR = 59;

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
    // A course with no zone falls back to the reader's, which has to be said explicitly: an instant parsed in UTC mode
    // stays in UTC mode otherwise, and would then be read an hour or more away from the day it belongs to.
    return timeZone ? instant.tz(timeZone) : instant.local();
}

/** The last minute of a day, which is how the end of a whole-day holiday is stored. */
export function endOfHolidayDay(day: dayjs.Dayjs): dayjs.Dayjs {
    return day.startOf('day').set('hour', LAST_HOUR_OF_DAY).set('minute', LAST_MINUTE_OF_HOUR).startOf('minute');
}

/** Whether a holiday takes its first day from the very beginning, rather than starting partway through it. */
export function startsAtBeginningOfDay(instant: dayjs.Dayjs): boolean {
    return instant.hour() === FIRST_HOUR_OF_DAY && instant.minute() === FIRST_MINUTE_OF_HOUR;
}

/** Whether a holiday holds its last day to the very end, rather than releasing it partway through. */
export function endsAtEndOfDay(instant: dayjs.Dayjs): boolean {
    return instant.hour() === LAST_HOUR_OF_DAY && instant.minute() === LAST_MINUTE_OF_HOUR;
}

/** Whether a span runs from the first minute of its first day to the last of its last, rather than being narrowed. */
export function coversWholeDays(start: dayjs.Dayjs, end: dayjs.Dayjs): boolean {
    return startsAtBeginningOfDay(start) && endsAtEndOfDay(end);
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
