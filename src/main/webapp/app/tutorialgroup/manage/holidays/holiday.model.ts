import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

/** `YYYY-MM-DD`, the key both the session counts and the calendar grid are indexed by. */
export const DAY_KEY_FORMAT = 'YYYY-MM-DD';

/**
 * One day on which a holiday cancels sessions.
 *
 * A free period is stored as a start and an end instant, which lets a single row span several days. The page creates
 * only single-day holidays, but rows created before it did can still span more, so every row is expanded into one
 * occurrence per day it covers. `period` stays the row behind the occurrence, so editing or deleting any occurrence acts
 * on the whole row rather than on the day the reader happened to click.
 */
export interface HolidayOccurrence {
    readonly period: TutorialGroupFreePeriod;
    /** Start of the day this occurrence falls on, in the time zone of the tutorial groups configuration. */
    readonly day: dayjs.Dayjs;
    readonly dayKey: string;
    readonly reason: string;
    /** True when the occurrence covers the whole day rather than a span within it. */
    readonly wholeDay: boolean;
    /** The span within the day, absent when `wholeDay`. */
    readonly startTime?: string;
    readonly endTime?: string;
    /** True when the underlying row covers more than this one day, which the page can no longer produce. */
    readonly partOfMultiDayPeriod: boolean;
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

/**
 * Whether a free period covers its days completely.
 *
 * A whole-day holiday is stored as 00:00 to 23:59, which is what the create dialog writes and what the import would
 * write. Anything else is a span within a day and keeps its times.
 */
export function coversWholeDay(period: TutorialGroupFreePeriod, timeZone: string | undefined): boolean {
    if (!period.start || !period.end) {
        return false;
    }
    const start = inCourseZone(period.start, timeZone);
    const end = inCourseZone(period.end, timeZone);
    return start.hour() === 0 && start.minute() === 0 && end.hour() === 23 && end.minute() === 59;
}

/**
 * Expands the free periods of a course into one occurrence per day, ascending.
 *
 * Rows without a start or an end are skipped rather than rendered at an arbitrary day: they cannot be placed on the
 * calendar, and showing them somewhere wrong is worse than leaving them out of the grid.
 */
export function toOccurrences(periods: readonly TutorialGroupFreePeriod[], timeZone: string | undefined): HolidayOccurrence[] {
    const occurrences: HolidayOccurrence[] = [];
    for (const period of periods) {
        if (!period.start || !period.end) {
            continue;
        }
        const start = inCourseZone(period.start, timeZone);
        const end = inCourseZone(period.end, timeZone);
        const wholeDay = coversWholeDay(period, timeZone);
        const partOfMultiDayPeriod = !start.isSame(end, 'day');

        let day = start.startOf('day');
        const lastDay = end.startOf('day');
        while (!day.isAfter(lastDay)) {
            occurrences.push({
                period,
                day,
                dayKey: day.format(DAY_KEY_FORMAT),
                reason: period.reason ?? '',
                wholeDay: wholeDay || partOfMultiDayPeriod,
                startTime: wholeDay || partOfMultiDayPeriod ? undefined : start.format('HH:mm'),
                endTime: wholeDay || partOfMultiDayPeriod ? undefined : end.format('HH:mm'),
                partOfMultiDayPeriod,
            });
            day = day.add(1, 'day');
        }
    }
    return occurrences.sort((left, right) => left.day.valueOf() - right.day.valueOf());
}

/** Groups occurrences by their day, so the calendar grid can look a day up without scanning the list. */
export function groupByDay(occurrences: readonly HolidayOccurrence[]): Map<string, HolidayOccurrence[]> {
    const byDay = new Map<string, HolidayOccurrence[]>();
    for (const occurrence of occurrences) {
        const existing = byDay.get(occurrence.dayKey);
        if (existing) {
            existing.push(occurrence);
        } else {
            byDay.set(occurrence.dayKey, [occurrence]);
        }
    }
    return byDay;
}
