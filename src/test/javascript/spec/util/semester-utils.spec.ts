import { getSemesters } from 'app/utils/semester-utils';

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
});
