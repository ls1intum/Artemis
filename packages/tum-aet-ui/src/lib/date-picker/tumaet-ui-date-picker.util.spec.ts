import dayjs from 'dayjs/esm';
import { buildMonthMatrix, combineDateAndTime, formatDisplay, matchesDisplayFormat, parseDisplay, valuesEqual } from './tumaet-ui-date-picker.util';

describe('tumaet-ui-date-picker util', () => {
    it('round-trips a valid date+time', () => {
        const parsed = parseDisplay('13.06.2026 08:30');
        expect(parsed).toBeDefined();
        expect(formatDisplay(parsed!)).toBe('13.06.2026 08:30');
    });

    it('returns undefined for empty input', () => {
        expect(parseDisplay('   ')).toBeUndefined();
    });

    it('strictly rejects malformed input', () => {
        expect(parseDisplay('13.6.2026 8:30')).toBeUndefined();
        expect(parseDisplay('13.06.2026 08:30x')).toBeUndefined();
        expect(parseDisplay('31.02.2026 08:30')).toBeUndefined();
        expect(parseDisplay('13.06.2026')).toBeUndefined();
    });

    it('reads pasted text whose whitespace differs from the plain space the picker displays', () => {
        const expected = '13.06.2026 08:30';
        for (const text of ['13.06.2026\u00a008:30', '13.06.2026  08:30', '13.06.2026\t08:30', ' 13.06.2026 08:30 ', '\u00a013.06.2026 08:30\u00a0']) {
            expect(formatDisplay(parseDisplay(text)!)).toBe(expected);
            expect(matchesDisplayFormat(text, false)).toBe(true);
        }
        expect(parseDisplay('13.06.2026\u00a0', false, undefined, true)?.format('DD.MM.YYYY')).toBe('13.06.2026');
        expect(matchesDisplayFormat('\u00a008:30', true)).toBe(true);
    });

    it('still rejects text that only looks like a date once whitespace is ignored', () => {
        expect(parseDisplay('13.06.202 6 08:30')).toBeUndefined();
        expect(matchesDisplayFormat('13.06.2026 0 8:30', false)).toBe(false);
        expect(matchesDisplayFormat('13.06.2026 08:30', false, true)).toBe(false);
    });

    it('builds a 6x7 Monday-first month matrix including the boundaries', () => {
        const weeks = buildMonthMatrix(dayjs('2026-06-15'));
        expect(weeks).toHaveLength(6);
        expect(weeks.every((week) => week.length === 7)).toBe(true);
        expect(weeks[0][0].day()).toBe(1);
        const flat = weeks.flat();
        expect(flat.some((day) => day.month() === 5 && day.date() === 1)).toBe(true);
        expect(flat.some((day) => day.month() === 5 && day.date() === 30)).toBe(true);
    });

    it('includes Feb 29 in a leap-year grid', () => {
        const flat = buildMonthMatrix(dayjs('2024-02-10')).flat();
        expect(flat.some((day) => day.month() === 1 && day.date() === 29)).toBe(true);
    });

    it('combines a day with a time at minute precision', () => {
        const combined = combineDateAndTime(dayjs('2026-06-13T00:00:00'), dayjs('2000-01-01T09:45:00'));
        expect(combined.hour()).toBe(9);
        expect(combined.minute()).toBe(45);
        expect(combined.date()).toBe(13);
        expect(combined.second()).toBe(0);
    });

    it('compares values to the minute', () => {
        expect(valuesEqual(undefined, undefined)).toBe(true);
        expect(valuesEqual(dayjs(), undefined)).toBe(false);
        expect(valuesEqual(dayjs('2026-06-13T08:30:10'), dayjs('2026-06-13T08:30:50'))).toBe(true);
        expect(valuesEqual(dayjs('2026-06-13T08:30'), dayjs('2026-06-13T08:31'))).toBe(false);
    });
});
