import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { coversWholeDay, groupByDay, toOccurrences } from 'app/tutorialgroup/manage/holidays/holiday.model';

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
    describe('coversWholeDay', () => {
        it('should recognise a period stored as midnight to 23:59 in the course time zone', () => {
            // 23:00Z on 16 Dec is 00:00 on 17 Dec in Berlin, and 22:59Z on 17 Dec is 23:59 the same day.
            expect(coversWholeDay(period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00'), TIME_ZONE)).toBe(true);
        });

        it('should not treat a span within a day as a whole day', () => {
            expect(coversWholeDay(period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00'), TIME_ZONE)).toBe(false);
        });

        it('should return false when the period has no start or end', () => {
            const incomplete = new TutorialGroupFreePeriod();
            incomplete.id = 1;
            expect(coversWholeDay(incomplete, TIME_ZONE)).toBe(false);
        });
    });

    describe('toOccurrences', () => {
        it('should keep the times of a holiday that only covers part of a day', () => {
            const occurrences = toOccurrences([period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00', 'Dies Academicus')], TIME_ZONE);

            expect(occurrences).toHaveLength(1);
            expect(occurrences[0].wholeDay).toBe(false);
            expect(occurrences[0].startTime).toBe('09:15');
            expect(occurrences[0].endTime).toBe('13:45');
            expect(occurrences[0].dayKey).toBe('2025-12-04');
            expect(occurrences[0].partOfMultiDayPeriod).toBe(false);
        });

        it('should expand a legacy multi-day period into one occurrence per day', () => {
            // Rows like this can no longer be created, but ones stored before the rework still have to render.
            const occurrences = toOccurrences([period(7, '2025-12-16T23:00:00', '2025-12-19T22:59:00', 'Christmas holidays')], TIME_ZONE);

            expect(occurrences.map((occurrence) => occurrence.dayKey)).toEqual(['2025-12-17', '2025-12-18', '2025-12-19']);
            expect(occurrences.every((occurrence) => occurrence.partOfMultiDayPeriod)).toBe(true);
            // Every occurrence points back at the one row, so deleting any of them deletes the whole period.
            expect(occurrences.every((occurrence) => occurrence.period.id === 7)).toBe(true);
        });

        it('should treat every day of a multi-day period as a whole day', () => {
            const occurrences = toOccurrences([period(7, '2025-12-16T23:00:00', '2025-12-19T22:59:00')], TIME_ZONE);

            expect(occurrences.every((occurrence) => occurrence.wholeDay)).toBe(true);
            expect(occurrences.every((occurrence) => occurrence.startTime === undefined)).toBe(true);
        });

        it('should skip periods that have no start or end rather than placing them on an arbitrary day', () => {
            const incomplete = new TutorialGroupFreePeriod();
            incomplete.id = 9;

            expect(toOccurrences([incomplete], TIME_ZONE)).toEqual([]);
        });

        it('should sort occurrences by day', () => {
            const occurrences = toOccurrences(
                [period(2, '2025-12-24T23:00:00', '2025-12-25T22:59:00', 'Christmas Day'), period(1, '2025-12-03T23:00:00', '2025-12-04T22:59:00', 'Earlier')],
                TIME_ZONE,
            );

            expect(occurrences.map((occurrence) => occurrence.dayKey)).toEqual(['2025-12-04', '2025-12-25']);
        });

        it('should fall back to the reader time zone when the course has none', () => {
            const occurrences = toOccurrences([period(1, '2025-12-04T08:15:00', '2025-12-04T12:45:00')], undefined);

            expect(occurrences).toHaveLength(1);
            expect(occurrences[0].reason).toBe('Holiday');
        });
    });

    describe('groupByDay', () => {
        it('should collect every occurrence that falls on the same day', () => {
            const occurrences = toOccurrences(
                [period(1, '2025-12-16T23:00:00', '2025-12-17T22:59:00', 'Christmas holidays'), period(2, '2025-12-17T09:00:00', '2025-12-17T10:00:00', 'Overlapping')],
                TIME_ZONE,
            );

            const byDay = groupByDay(occurrences);

            expect(byDay.get('2025-12-17')).toHaveLength(2);
            expect(byDay.get('2025-12-18')).toBeUndefined();
        });
    });
});
