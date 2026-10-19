import { afterEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import {
    applySemesterToDates,
    generateCourseShortName,
    getCurrentAndFutureSemesters,
    getCurrentSemester,
    getDefaultSemester,
    getNextSemester,
    getSemesterDateRange,
    getSemesterProgress,
    getSemesters,
} from 'app/foundation/util/semester-utils';

describe('SemesterUtils', () => {
    describe('getSemesters', () => {
        it('should get semesters around current year', () => {
            vi.useFakeTimers().setSystemTime(new Date('2019-01-10'));
            const expectedSemesters = ['WS20/21', 'SS20', 'WS19/20', 'SS19', 'WS18/19', 'SS18'];

            const semesters = getSemesters();

            //expect length to be 6 (years 2018-2020)
            expect(semesters).toHaveLength(6);
            expect(semesters).toEqual(expectedSemesters);
        });
    });

    describe('getCurrentSemester', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should return winter semester for October-December', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            vi.useFakeTimers().setSystemTime(new Date('2025-11-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            vi.useFakeTimers().setSystemTime(new Date('2025-12-15'));
            expect(getCurrentSemester()).toBe('WS25/26');
        });

        it('should return winter semester for January-March (continuation)', () => {
            vi.useFakeTimers().setSystemTime(new Date('2026-01-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            vi.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            vi.useFakeTimers().setSystemTime(new Date('2026-03-15'));
            expect(getCurrentSemester()).toBe('WS25/26');
        });

        it('should return summer semester for April-September', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-04-15'));
            expect(getCurrentSemester()).toBe('SS25');

            vi.useFakeTimers().setSystemTime(new Date('2025-07-15'));
            expect(getCurrentSemester()).toBe('SS25');

            vi.useFakeTimers().setSystemTime(new Date('2025-09-15'));
            expect(getCurrentSemester()).toBe('SS25');
        });
    });

    describe('getNextSemester', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should return summer semester when in winter semester (Oct-Dec)', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getNextSemester()).toBe('SS26');
        });

        it('should return summer semester when in winter semester (Jan-Mar)', () => {
            vi.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getNextSemester()).toBe('SS26');
        });

        it('should return winter semester when in summer semester', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-06-15'));
            expect(getNextSemester()).toBe('WS25/26');
        });
    });

    describe('getSemesterProgress', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should return 0 at start of semester', () => {
            // Start of winter semester
            vi.useFakeTimers().setSystemTime(new Date(2025, 9, 1, 12));
            const progress = getSemesterProgress();
            expect(progress).toBeCloseTo(0, 0);
        });

        it('should return approximately 50 at mid-semester', () => {
            // Mid-winter semester (around mid-December)
            vi.useFakeTimers().setSystemTime(new Date(2025, 11, 15, 12));
            const progress = getSemesterProgress();
            expect(progress).toBeGreaterThan(40);
            expect(progress).toBeLessThan(60);
        });

        it('should return close to 100 at end of semester', () => {
            // End of winter semester
            vi.useFakeTimers().setSystemTime(new Date(2026, 2, 30, 12));
            const progress = getSemesterProgress();
            expect(progress).toBeGreaterThan(95);
        });
    });

    describe('getDefaultSemester', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should return current semester when progress < 50%', () => {
            // Early in winter semester
            vi.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getDefaultSemester()).toBe('WS25/26');
        });

        it('should return next semester when progress >= 50%', () => {
            // Late in winter semester (February)
            vi.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getDefaultSemester()).toBe('SS26');
        });
    });

    describe('getCurrentAndFutureSemesters', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should return current and future semesters only', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            const semesters = getCurrentAndFutureSemesters();

            expect(semesters).toContain('WS25/26');
            expect(semesters).toContain('SS26');
            expect(semesters).toContain('WS26/27');
            expect(semesters).not.toContain('SS25'); // Past semester
            expect(semesters).not.toContain('WS24/25'); // Past semester
        });

        it('should start with current semester', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-06-15'));
            const semesters = getCurrentAndFutureSemesters();

            expect(semesters[0]).toBe('SS25');
        });

        it('should include winter semester spanning previous/current year in Jan-Mar', () => {
            vi.useFakeTimers().setSystemTime(new Date('2025-02-15'));
            const semesters = getCurrentAndFutureSemesters();

            // Current semester should be WS24/25 (winter semester spanning 2024/2025)
            expect(semesters[0]).toBe('WS24/25');
            expect(semesters).toContain('SS25');
            expect(semesters).toContain('WS25/26');
            expect(semesters).not.toContain('SS24'); // Past semester
        });

        it('should have SS26 as default while WS25/26 is still selectable in Jan-Mar 2026', () => {
            vi.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            const semesters = getCurrentAndFutureSemesters();

            // Default semester should be SS26 (next semester, since we're > 50% through WS)
            expect(getDefaultSemester()).toBe('SS26');

            // But WS25/26 (current semester) should still be available in the list
            expect(semesters).toContain('WS25/26');
            expect(semesters).toContain('SS26');
            expect(semesters[0]).toBe('WS25/26'); // Current semester is first in list
        });
    });

    describe('generateCourseShortName', () => {
        it('should generate short name from title and winter semester', () => {
            expect(generateCourseShortName('Introduction To Programming', 'WS25/26')).toBe('ITP2526');
        });

        it('should generate short name from title and summer semester', () => {
            expect(generateCourseShortName('Data Structures', 'SS25')).toBe('DS25');
        });

        it('should extract all digits from semester', () => {
            expect(generateCourseShortName('Test Course', 'WS25/26')).toBe('TC2526');
        });

        it('should use first letter of each word in title', () => {
            expect(generateCourseShortName('Advanced Database Systems', 'SS25')).toBe('ADS25');
        });

        it('should ignore non-alphanumeric first characters except digits', () => {
            expect(generateCourseShortName('123 Test Course', 'WS25/26')).toBe('1TC2526');
        });

        it('should handle single word title', () => {
            expect(generateCourseShortName('Programming', 'SS25')).toBe('P25');
        });

        it('should pad to minimum 3 characters when title is short', () => {
            expect(generateCourseShortName('AI', '')).toBe('ACR');
        });

        it('should pad to minimum 3 characters with single letter title', () => {
            expect(generateCourseShortName('X', '')).toBe('XCR');
        });

        it('should handle empty title', () => {
            expect(generateCourseShortName('', 'WS25/26')).toBe('2526');
        });

        it('should handle empty semester', () => {
            // "TC" from title needs 1 char padding → "TCC"
            expect(generateCourseShortName('Test Course', '')).toBe('TCC');
        });

        it('should handle both empty', () => {
            expect(generateCourseShortName('', '')).toBe('CRS');
        });

        it('should handle whitespace-only title', () => {
            expect(generateCourseShortName('   ', 'SS25')).toBe('25C');
        });

        it('should handle title with special characters', () => {
            expect(generateCourseShortName('C++ Programming', 'SS25')).toBe('CP25');
        });

        it('should handle null-like values gracefully', () => {
            expect(generateCourseShortName(null as unknown as string, 'SS25')).toBe('25C');
            expect(generateCourseShortName(undefined as unknown as string, 'SS25')).toBe('25C');
        });

        it('should uppercase all letters', () => {
            expect(generateCourseShortName('lower case title', 'ss25')).toBe('LCT25');
        });

        it('should handle German umlauts by ignoring them', () => {
            // Umlauts don't match /[A-Z0-9]/i so they should be skipped
            expect(generateCourseShortName('Übung', 'SS25')).toBe('25C');
        });
    });
});

describe('getSemesterDateRange', () => {
    it('maps a winter semester to October 1 through March 31 of the following year', () => {
        const range = getSemesterDateRange('WS25/26')!;
        expect(range.startDate.format('YYYY-MM-DD')).toBe('2025-10-01');
        expect(range.endDate.format('YYYY-MM-DD')).toBe('2026-03-31');
    });

    it('maps a summer semester to April 1 through September 30', () => {
        const range = getSemesterDateRange('SS25')!;
        expect(range.startDate.format('YYYY-MM-DD')).toBe('2025-04-01');
        expect(range.endDate.format('YYYY-MM-DD')).toBe('2025-09-30');
    });

    it('starts at the beginning and ends at the end of the day', () => {
        const range = getSemesterDateRange('SS25')!;
        expect(range.startDate.format('HH:mm')).toBe('00:00');
        expect(range.endDate.format('HH:mm')).toBe('23:59');
    });

    it.each(['', undefined, 'WS2025', '2025W', 'nonsense'])('returns undefined for %s', (semester) => {
        expect(getSemesterDateRange(semester as string | undefined)).toBeUndefined();
    });

    it('returns undefined when the year after the slash is not the following year', () => {
        expect(getSemesterDateRange('WS20/99')).toBeUndefined();
    });

    it('handles a winter semester that wraps the century', () => {
        const range = getSemesterDateRange('WS99/00')!;
        expect(range.startDate.format('YYYY-MM-DD')).toBe('2099-10-01');
        expect(range.endDate.format('YYYY-MM-DD')).toBe('2100-03-31');
    });
});

describe('applySemesterToDates', () => {
    it('fills in dates that are empty', () => {
        const result = applySemesterToDates('WS25/26', undefined, undefined, undefined);

        expect(result.startDate!.format('YYYY-MM-DD')).toBe('2025-10-01');
        expect(result.endDate!.format('YYYY-MM-DD')).toBe('2026-03-31');
    });

    it('replaces dates that still equal the previous semester range', () => {
        const previousRange = getSemesterDateRange('WS25/26')!;

        const result = applySemesterToDates('SS26', 'WS25/26', previousRange.startDate, previousRange.endDate);

        expect(result.startDate!.format('YYYY-MM-DD')).toBe('2026-04-01');
        expect(result.endDate!.format('YYYY-MM-DD')).toBe('2026-09-30');
    });

    it('passes a hand-set date through unchanged while its untouched sibling still follows the semester', () => {
        const previousRange = getSemesterDateRange('WS25/26')!;
        const handPickedStartDate = dayjs('2025-11-05');

        const result = applySemesterToDates('SS26', 'WS25/26', handPickedStartDate, previousRange.endDate);

        expect(result.startDate!.format('YYYY-MM-DD')).toBe('2025-11-05');
        expect(result.endDate!.format('YYYY-MM-DD')).toBe('2026-09-30');
    });

    it('changes nothing for an unparseable semester', () => {
        const startDate = dayjs('2025-11-05');
        const endDate = dayjs('2026-01-10');

        const result = applySemesterToDates('not-a-semester', 'WS25/26', startDate, endDate);

        expect(result.startDate).toBe(startDate);
        expect(result.endDate).toBe(endDate);
    });
});

describe('getSemesters with an extra value', () => {
    it('does not offer an empty semester any more', () => {
        expect(getSemesters()).not.toContain('');
    });

    it('appends a legacy semester that the generated range does not cover', () => {
        expect(getSemesters('WS16/17')).toContain('WS16/17');
    });

    it('does not duplicate a semester that is already generated', () => {
        const semesters = getSemesters('SS20');
        expect(semesters.filter((semester) => semester === 'SS20')).toHaveLength(1);
    });
});
