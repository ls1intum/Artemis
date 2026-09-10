import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { coversWholeDays, endOfHolidayDay, holidaysByDay, toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';

const TIME_ZONE = 'Europe/Berlin';

function period(id: number, start: string, end: string, reason = 'Holiday'): TutorialGroupFreePeriod {
    const freePeriod = new TutorialGroupFreePeriod();
    freePeriod.id = id;
    // The server hands out UTC instants, which is what the page converts into the course's zone.
    freePeriod.start = dayjs.utc(start);
    freePeriod.end = dayjs.utc(end);
    freePeriod.reason = reason;
    return freePeriod;
}

describe('holiday model', () => {
    describe('coversWholeDays', () => {
        it('should recognise a span running from midnight to 23:59', () => {
            expect(coversWholeDays(dayjs('2025-12-17T00:00'), dayjs('2025-12-17T23:59'))).toBe(true);
        });

        it('should recognise a run of whole days', () => {
            expect(coversWholeDays(dayjs('2025-12-17T00:00'), dayjs('2025-12-31T23:59'))).toBe(true);
        });

        it('should not treat a span narrowed within a day as whole days', () => {
            expect(coversWholeDays(dayjs('2025-12-04T09:15'), dayjs('2025-12-04T13:45'))).toBe(false);
        });
    });

    describe('endOfHolidayDay', () => {
        it('should return the last minute of the day', () => {
            expect(endOfHolidayDay(dayjs('2025-12-04T08:30')).format('YYYY-MM-DD HH:mm:ss')).toBe('2025-12-04 23:59:00');
        });
    });

    describe('toHolidays', () => {
        it('should read a whole-day holiday in the time zone of the course', () => {
            // 23:00Z on 16 Dec is 00:00 on 17 Dec in Berlin, and 22:59Z on 17 Dec is 23:59 the same day.
            const [holiday] = toHolidays([period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00', 'Christmas holidays')], TIME_ZONE);

            expect(holiday.wholeDay).toBe(true);
            expect(holiday.spansMultipleDays).toBe(false);
            expect(holiday.dayCount).toBe(1);
            expect(holiday.start.format('YYYY-MM-DD')).toBe('2025-12-17');
        });

        it('should keep a holiday that only covers part of a day as a span with times', () => {
            const [holiday] = toHolidays([period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus')], TIME_ZONE);

            expect(holiday.wholeDay).toBe(false);
            expect(holiday.startTime).toBe('09:15');
            expect(holiday.endTime).toBe('13:45');
        });

        it('should keep a run of days as one holiday rather than splitting it', () => {
            const [holiday] = toHolidays([period(7, '2025-12-16T23:00:00', '2025-12-31T22:59:00', 'Christmas holidays')], TIME_ZONE);

            expect(holiday.spansMultipleDays).toBe(true);
            expect(holiday.wholeDay).toBe(true);
            expect(holiday.dayCount).toBe(15);
        });

        it('should skip periods without a start or an end rather than placing them on an arbitrary day', () => {
            const incomplete = new TutorialGroupFreePeriod();
            incomplete.id = 9;

            expect(toHolidays([incomplete], TIME_ZONE)).toEqual([]);
        });

        it('should sort holidays by start', () => {
            const holidays = toHolidays(
                [period(2, '2025-12-24T23:00:00', '2025-12-25T22:59:00', 'Christmas Day'), period(1, '2025-12-03T23:00:00', '2025-12-04T22:59:00', 'Earlier')],
                TIME_ZONE,
            );

            expect(holidays.map((holiday) => holiday.reason)).toEqual(['Earlier', 'Christmas Day']);
        });

        it('should fall back to the reader time zone when the course has none', () => {
            const holidays = toHolidays([period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00')], undefined);

            expect(holidays).toHaveLength(1);
        });
    });

    describe('holidaysByDay', () => {
        it('should index a multi-day holiday under every day it covers', () => {
            const holidays = toHolidays([period(7, '2025-12-16T23:00:00', '2025-12-19T22:59:00', 'Christmas holidays')], TIME_ZONE);

            const byDay = holidaysByDay(holidays);

            expect([...byDay.keys()]).toEqual(['2025-12-17', '2025-12-18', '2025-12-19']);
            // Every day points back at the one holiday, so editing from any of them edits the whole thing.
            expect(byDay.get('2025-12-18')![0].period.id).toBe(7);
        });

        it('should collect every holiday that falls on the same day', () => {
            const holidays = toHolidays(
                [period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00', 'Christmas holidays'), period(2, '2025-12-17T09:00:00', '2025-12-17T10:00:00', 'Overlapping')],
                TIME_ZONE,
            );

            expect(holidaysByDay(holidays).get('2025-12-17')).toHaveLength(2);
        });
    });
});
