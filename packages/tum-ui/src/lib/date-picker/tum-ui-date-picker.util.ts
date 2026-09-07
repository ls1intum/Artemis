import dayjs from 'dayjs/esm';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';

dayjs.extend(customParseFormat);

export const DISPLAY_FORMAT = 'DD.MM.YYYY HH:mm';

export const TIME_ONLY_FORMAT = 'HH:mm';

export const DISPLAY_REGEX = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;

export const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;

/** The text format one picker reads and writes: a time on its own, or a full date and time. */
export function displayFormat(timeOnly: boolean): string {
    return timeOnly ? TIME_ONLY_FORMAT : DISPLAY_FORMAT;
}

/** Whether `text` is shaped like a complete entry for that format, used to flag an incomplete one on blur. */
export function matchesDisplayFormat(text: string, timeOnly: boolean): boolean {
    return timeOnly ? TIME_REGEX.test(text) : DISPLAY_REGEX.test(text);
}

/**
 * Parses text in the picker's format. A time on its own carries no date, so it is placed on `onDate` - the
 * value already held, or today - which keeps the date stable while only the time is edited.
 */
export function parseDisplay(text: string, timeOnly = false, onDate?: dayjs.Dayjs): dayjs.Dayjs | undefined {
    const trimmed = text.trim();
    if (!trimmed) {
        return undefined;
    }
    const parsed = dayjs(trimmed, displayFormat(timeOnly), true);
    if (!parsed.isValid()) {
        return undefined;
    }
    if (!timeOnly) {
        return parsed;
    }
    return combineDateAndTime(onDate ?? dayjs(), parsed);
}

export function formatDisplay(value: dayjs.Dayjs, timeOnly = false): string {
    return value.format(displayFormat(timeOnly));
}

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

export function combineDateAndTime(date: dayjs.Dayjs, time: dayjs.Dayjs): dayjs.Dayjs {
    return date.hour(time.hour()).minute(time.minute()).second(0).millisecond(0);
}

export function valuesEqual(a: dayjs.Dayjs | undefined, b: dayjs.Dayjs | undefined): boolean {
    if (!a && !b) {
        return true;
    }
    if (!a || !b) {
        return false;
    }
    return a.isSame(b, 'minute');
}
