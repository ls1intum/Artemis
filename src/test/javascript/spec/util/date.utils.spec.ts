import { convertDateFromClient, convertDateFromServer, dayOfWeekZeroSundayToZeroMonday, toISO8601DateString, toISO8601DateTimeString } from 'app/utils/date.utils';
import dayjs from 'dayjs/esm';

describe('DateUtils', () => {
    // month of js date is 0-based
    const exampleDate = new Date(2019, 8, 10, 12, 30, 20);
    const exampleISO8601DateString = '2019-09-10';
    const exampleISO8601DateTimeString = '2019-09-10T12:30:20';
    const exampleDayjs = dayjs('2019-01-25');

    describe('convertDateFromClient', () => {
        it('should convert date to json', () => {
            expect(convertDateFromClient(exampleDayjs)).toEqual(exampleDayjs.toJSON());
        });

        it('should return undefined if date is not valid', () => {
            expect(convertDateFromClient(dayjs('invalid'))).toBeUndefined();
        });

        it('should return undefined if date is undefined', () => {
            expect(convertDateFromClient(undefined)).toBeUndefined();
        });
    });

    describe('convertDateFromServer', () => {
        it('should convert dayjs to dayjs', () => {
            expect(convertDateFromServer(exampleDayjs)).toEqual(exampleDayjs);
        });
    });
    describe('toISO8601DateString', () => {
        it('should convert date to iso string', () => {
            const isoString = toISO8601DateString(exampleDate);
            expect(isoString).toBe(exampleISO8601DateString);
        });

        it('should return null or undefined', () => {
            expect(toISO8601DateString(undefined)).toBeUndefined();
            expect(toISO8601DateString(null)).toBeNull();
        });
    });

    describe('toISO8601DateTimeString', () => {
        it('should convert date to iso string', () => {
            const isoString = toISO8601DateTimeString(exampleDate);
            expect(isoString).toBe(exampleISO8601DateTimeString);
        });

        it('should return null or undefined', () => {
            expect(toISO8601DateTimeString(undefined)).toBeUndefined();
            expect(toISO8601DateTimeString(null)).toBeNull();
        });
    });

    describe('dayOfWeekZeroSundayToZeroMonday', () => {
        it('should convert day of week', () => {
            // From 0: Sunday, 1: Monday, 2: Tuesday, 3: Wednesday, 4: Thursday, 5: Friday, 6: Saturday
            // To 0: Monday, 1: Tuesday, 2: Wednesday, 3: Thursday, 4: Friday, 5: Saturday, 6: Sunday
            expect(dayOfWeekZeroSundayToZeroMonday(0)).toBe(6);
            expect(dayOfWeekZeroSundayToZeroMonday(1)).toBe(0);
            expect(dayOfWeekZeroSundayToZeroMonday(2)).toBe(1);
            expect(dayOfWeekZeroSundayToZeroMonday(3)).toBe(2);
            expect(dayOfWeekZeroSundayToZeroMonday(4)).toBe(3);
            expect(dayOfWeekZeroSundayToZeroMonday(5)).toBe(4);
            expect(dayOfWeekZeroSundayToZeroMonday(6)).toBe(5);
        });

        it('should throw error if day of week is not in range', () => {
            expect(() => dayOfWeekZeroSundayToZeroMonday(-1)).toThrow();
            expect(() => dayOfWeekZeroSundayToZeroMonday(7)).toThrow();
        });
    });
});
