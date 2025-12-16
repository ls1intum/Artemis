import {
    generateCourseShortName,
    getCurrentAndFutureSemesters,
    getCurrentSemester,
    getDefaultSemester,
    getNextSemester,
    getSemesterProgress,
    getSemesters,
} from 'app/shared/util/semester-utils';

describe('SemesterUtils', () => {
    describe('getSemesters', () => {
        it('should get semesters around current year', () => {
            jest.useFakeTimers().setSystemTime(new Date('2019-01-10'));
            const expectedSemesters = ['WS20/21', 'SS20', 'WS19/20', 'SS19', 'WS18/19', 'SS18', ''];

            const semesters = getSemesters();

            //expect length to be 7 (years 2018-2020) + one empty value
            expect(semesters).toHaveLength(7);
            expect(semesters).toEqual(expectedSemesters);
        });
    });

    describe('getCurrentSemester', () => {
        afterEach(() => {
            jest.useRealTimers();
        });

        it('should return winter semester for October-December', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            jest.useFakeTimers().setSystemTime(new Date('2025-11-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            jest.useFakeTimers().setSystemTime(new Date('2025-12-15'));
            expect(getCurrentSemester()).toBe('WS25/26');
        });

        it('should return winter semester for January-March (continuation)', () => {
            jest.useFakeTimers().setSystemTime(new Date('2026-01-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            jest.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getCurrentSemester()).toBe('WS25/26');

            jest.useFakeTimers().setSystemTime(new Date('2026-03-15'));
            expect(getCurrentSemester()).toBe('WS25/26');
        });

        it('should return summer semester for April-September', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-04-15'));
            expect(getCurrentSemester()).toBe('SS25');

            jest.useFakeTimers().setSystemTime(new Date('2025-07-15'));
            expect(getCurrentSemester()).toBe('SS25');

            jest.useFakeTimers().setSystemTime(new Date('2025-09-15'));
            expect(getCurrentSemester()).toBe('SS25');
        });
    });

    describe('getNextSemester', () => {
        afterEach(() => {
            jest.useRealTimers();
        });

        it('should return summer semester when in winter semester (Oct-Dec)', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getNextSemester()).toBe('SS26');
        });

        it('should return summer semester when in winter semester (Jan-Mar)', () => {
            jest.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getNextSemester()).toBe('SS26');
        });

        it('should return winter semester when in summer semester', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-06-15'));
            expect(getNextSemester()).toBe('WS25/26');
        });
    });

    describe('getSemesterProgress', () => {
        afterEach(() => {
            jest.useRealTimers();
        });

        it('should return 0 at start of semester', () => {
            // Start of winter semester
            jest.useFakeTimers().setSystemTime(new Date('2025-10-01'));
            const progress = getSemesterProgress();
            expect(progress).toBeCloseTo(0, 0);
        });

        it('should return approximately 50 at mid-semester', () => {
            // Mid-winter semester (around mid-December)
            jest.useFakeTimers().setSystemTime(new Date('2025-12-15'));
            const progress = getSemesterProgress();
            expect(progress).toBeGreaterThan(40);
            expect(progress).toBeLessThan(60);
        });

        it('should return close to 100 at end of semester', () => {
            // End of winter semester
            jest.useFakeTimers().setSystemTime(new Date('2026-03-30'));
            const progress = getSemesterProgress();
            expect(progress).toBeGreaterThan(95);
        });
    });

    describe('getDefaultSemester', () => {
        afterEach(() => {
            jest.useRealTimers();
        });

        it('should return current semester when progress < 50%', () => {
            // Early in winter semester
            jest.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            expect(getDefaultSemester()).toBe('WS25/26');
        });

        it('should return next semester when progress >= 50%', () => {
            // Late in winter semester (February)
            jest.useFakeTimers().setSystemTime(new Date('2026-02-15'));
            expect(getDefaultSemester()).toBe('SS26');
        });
    });

    describe('getCurrentAndFutureSemesters', () => {
        afterEach(() => {
            jest.useRealTimers();
        });

        it('should return current and future semesters only', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-10-15'));
            const semesters = getCurrentAndFutureSemesters();

            expect(semesters).toContain('WS25/26');
            expect(semesters).toContain('SS26');
            expect(semesters).toContain('WS26/27');
            expect(semesters).not.toContain('SS25'); // Past semester
            expect(semesters).not.toContain('WS24/25'); // Past semester
        });

        it('should start with current semester', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-06-15'));
            const semesters = getCurrentAndFutureSemesters();

            expect(semesters[0]).toBe('SS25');
        });

        it('should include winter semester spanning previous/current year in Jan-Mar', () => {
            jest.useFakeTimers().setSystemTime(new Date('2025-02-15'));
            const semesters = getCurrentAndFutureSemesters();

            // Current semester should be WS24/25 (winter semester spanning 2024/2025)
            expect(semesters[0]).toBe('WS24/25');
            expect(semesters).toContain('SS25');
            expect(semesters).toContain('WS25/26');
            expect(semesters).not.toContain('SS24'); // Past semester
        });

        it('should have SS26 as default while WS25/26 is still selectable in Jan-Mar 2026', () => {
            jest.useFakeTimers().setSystemTime(new Date('2026-02-15'));
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
