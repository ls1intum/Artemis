import dayjs from 'dayjs/esm';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';

dayjs.extend(customParseFormat);

export const DISPLAY_FORMAT = 'DD.MM.YYYY HH:mm';

export const DISPLAY_REGEX = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;

export const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;

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
