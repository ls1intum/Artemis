import dayjs from 'dayjs/esm';
import { getSemesters } from 'app/utils/semester-utils';

describe('SemesterUtils', () => {
    describe('getSemesters', () => {
        it('should get semesters around current year', () => {
            jest.useFakeTimers().setSystemTime(new Date('2020-01-10'));

            const semesters = getSemesters();
            const years = dayjs().year() - 2018 + 1;

            //expect length to be 9 (years 2018-2021) + one empty value
            expect(semesters).toHaveLength(9);
            expect(semesters.last()).toBe('');
            for (let i = 0; i <= years; i++) {
                expect(semesters[2 * i]).toBe('WS' + (18 + years - i) + '/' + (19 + years - i));
                expect(semesters[2 * i + 1]).toBe('SS' + (18 + years - i));
            }
        });
    });
});
