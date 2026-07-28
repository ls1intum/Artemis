import dayjs from 'dayjs/esm';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';

dayjs.extend(customParseFormat);

/** Display/parse format for the date+time picker (24h, minute precision, no seconds). */
export const DISPLAY_FORMAT = 'DD.MM.YYYY HH:mm';
/** Full-format guard used on blur to reject a valid-prefix-plus-trailing-garbage string. */
export const DISPLAY_REGEX = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
/** 24h HH:mm time guard for the time field. */
export const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Strictly parse `DD.MM.YYYY HH:mm`; returns undefined for empty or unparseable input. */
export function parseDisplay(text: string): dayjs.Dayjs | undefined {
    const trimmed = text.trim();
    if (!trimmed) {
        return undefined;
    }
    const parsed = dayjs(trimmed, DISPLAY_FORMAT, true);
    return parsed.isValid() ? parsed : undefined;
}

export function formatDisplay(value: dayjs.Dayjs): string {
    return value.format(DISPLAY_FORMAT);
}

/**
 * Build a 6×7 Monday-first month matrix of dayjs days anchored on `month`.
 * Always 42 cells (stable grid height); leading/trailing cells belong to the adjacent months.
 */
export function buildMonthMatrix(month: dayjs.Dayjs): dayjs.Dayjs[][] {
    const startOfMonth = month.startOf('month');
    const offset = (startOfMonth.day() + 6) % 7;
    let cursor = startOfMonth.subtract(offset, 'day').startOf('day');
    const weeks: dayjs.Dayjs[][] = [];
    for (let week = 0; week < 6; week++) {
        const days: dayjs.Dayjs[] = [];
        for (let day = 0; day < 7; day++) {
            days.push(cursor);
            cursor = cursor.add(1, 'day');
        }
        weeks.push(days);
    }
    return weeks;
}

/** Combine a day with a time-of-day into a single dayjs at minute precision. */
export function combineDateAndTime(date: dayjs.Dayjs, time: dayjs.Dayjs): dayjs.Dayjs {
    return date.hour(time.hour()).minute(time.minute()).second(0).millisecond(0);
}

/** True if both are absent, or both present and equal to the minute. */
export function valuesEqual(a: dayjs.Dayjs | undefined, b: dayjs.Dayjs | undefined): boolean {
    if (!a && !b) {
        return true;
    }
    if (!a || !b) {
        return false;
    }
    return a.isSame(b, 'minute');
}
