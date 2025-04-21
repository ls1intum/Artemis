import { MIN_ITEMS_TO_GROUP_BY_WEEK, WeekGroupingUtil } from './week-grouping.util';
import { SidebarCardElement } from '../types/sidebar';
import dayjs from 'dayjs/esm';

describe('WeekGroupingUtil', () => {
    it('returns a single group without header for the special exam sections', () => {
        const items: SidebarCardElement[] = [
            {
                title: 'Exam 1',
                id: 'e1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-01'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Exam 2',
                id: 'e2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-02'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
        ];

        ['real', 'test', 'attempt'].forEach((key) => {
            const groups = WeekGroupingUtil.getGroupedByWeek(items, key);
            expect(groups).toHaveLength(1);
            expect(groups[0].isNoDate).toBeTruthy();
            expect(groups[0].showDateHeader).toBeFalsy();
            expect(groups[0].items).toHaveLength(2);
        });
    });

    it('returns a single group while searching (no headers)', () => {
        const items: SidebarCardElement[] = [
            { title: 'Item 1', id: 'i1', size: 'M', startDate: dayjs('2024-01-01') },
            { title: 'Item 2', id: 'i2', size: 'M', startDate: dayjs('2024-01-02') },
            { title: 'Other', id: 'i3', size: 'M', startDate: dayjs('2024-01-03') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'lecture', 'item');
        expect(groups).toHaveLength(1);
        expect(groups[0].showDateHeader).toBeFalsy();
        expect(groups[0].items).toHaveLength(2);
        expect(groups[0].items.map((i) => i.title)).toEqual(['Item 1', 'Item 2']);
    });

    it('searches in both title and type for lectures', () => {
        const items: SidebarCardElement[] = [
            { title: 'Lecture 1', id: 'l1', size: 'M', type: 'lecture', startDate: dayjs('2024-01-01') },
            { title: 'Lecture 2', id: 'l2', size: 'M', startDate: dayjs('2024-01-02') },
            { title: 'Lecture 3', id: 'l3', size: 'M', startDate: dayjs('2024-01-03') },
            { title: 'Lecture 4', id: 'l4', size: 'M', startDate: dayjs('2024-01-04') },
            { title: 'Lecture 5', id: 'l5', size: 'M', startDate: dayjs('2024-01-05') },
            { title: 'Lecture 6', id: 'l6', size: 'M', startDate: dayjs('2024-01-07') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'lecture', 'lecture');
        expect(groups).toHaveLength(1);
        expect(groups[0].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Lecture 1', 'Lecture 2', 'Lecture 3', 'Lecture 4', 'Lecture 5', 'Lecture 6']));
    });

    it('displays correct week range title', () => {
        const items: SidebarCardElement[] = [
            { title: 'L1', id: 'm1', size: 'M', startDate: dayjs('2024-01-01') },
            { title: 'L2', id: 'w1', size: 'M', startDate: dayjs('2024-01-03') },
            { title: 'L3', id: 'w1', size: 'M', startDate: dayjs('2024-01-04') },
            { title: 'L4', id: 'm2', size: 'M', startDate: dayjs('2024-01-08') },
            { title: 'L5', id: 'w2', size: 'M', startDate: dayjs('2024-01-10') },
            { title: 'L6', id: 'w2', size: 'M', startDate: dayjs('2024-01-10') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'lecture');
        expect(groups).toHaveLength(2);

        // First group should be Week 2 (Jan 8-14)
        expect(groups[0].start!.format('DD MMM YYYY')).toBe('07 Jan 2024');
        expect(groups[0].end!.format('DD MMM YYYY')).toBe('13 Jan 2024');

        // Second group should be Week 1 (Jan 1-7)
        expect(groups[1].start!.format('DD MMM YYYY')).toBe('31 Dec 2023');
        expect(groups[1].end!.format('DD MMM YYYY')).toBe('06 Jan 2024');
    });

    it('keeps a single group when item count ≤ MIN_ITEMS_TO_GROUP_BY_WEEK', () => {
        const items: SidebarCardElement[] = Array.from({ length: MIN_ITEMS_TO_GROUP_BY_WEEK }, (_, i) => ({
            title: `Item ${i + 1}`,
            id: `s${i}`,
            size: 'M',
            exercise: {
                dueDate: dayjs(`2024-01-0${i + 1}`),
                numberOfAssessmentsOfCorrectionRounds: [],
                studentAssignedTeamIdComputed: false,
                secondCorrectionEnabled: false,
            },
        }));

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
        expect(groups).toHaveLength(1);
        expect(groups[0].showDateHeader).toBeFalsy();
        expect(groups[0].items).toHaveLength(MIN_ITEMS_TO_GROUP_BY_WEEK);
    });

    it('honours the date-priority order (dueDate > startDateWithTime > startDate)', () => {
        const items: SidebarCardElement[] = [
            {
                title: 'Due Date',
                id: 'p1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-01'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
                startDateWithTime: dayjs('2024-01-02'),
                startDate: dayjs('2024-01-03'),
            },
            {
                title: 'Start Time',
                id: 'p2',
                size: 'M',
                startDateWithTime: dayjs('2024-01-02'),
                startDate: dayjs('2024-01-03'),
            },
            {
                title: 'Start Date',
                id: 'p3',
                size: 'M',
                startDate: dayjs('2024-01-03'),
            },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
        expect(groups[0].items.map((i) => i.title)).toEqual(['Due Date', 'Start Time', 'Start Date']);
    });

    it('sorts items inside each group by date (descending)', () => {
        const items: SidebarCardElement[] = ['06', '05', '04', '03', '02', '01'].map((d) => ({
            title: `Item ${d}`,
            id: `d${d}`,
            size: 'M',
            exercise: {
                dueDate: dayjs(`2024-01-${d}`),
                numberOfAssessmentsOfCorrectionRounds: [],
                studentAssignedTeamIdComputed: false,
                secondCorrectionEnabled: false,
            },
        }));

        const [firstGroup] = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
        expect(firstGroup.items.map((i) => i.title)).toEqual(['Item 06', 'Item 05', 'Item 04', 'Item 03', 'Item 02', 'Item 01']);
    });

    it('splits week boundaries correctly', () => {
        const items: SidebarCardElement[] = [
            {
                title: 'Week 1 - Sun',
                id: 's1',
                size: 'M',
                startDate: dayjs('2024-01-07'),
            },
            {
                title: 'Week 1 - Mon',
                id: 'm1',
                size: 'M',
                startDate: dayjs('2024-01-08'),
            },
            {
                title: 'Week 1 - Sat',
                id: 't1',
                size: 'M',
                startDate: dayjs('2024-01-13'),
            },
            {
                title: 'Week 2 - Sun',
                id: 's2',
                size: 'M',
                startDate: dayjs('2024-01-14'),
            },
            {
                title: 'Week 2 - Mon',
                id: 'm2',
                size: 'M',
                startDate: dayjs('2024-01-15'),
            },
            {
                title: 'Week 2 - Sat',
                id: 't2',
                size: 'M',
                startDate: dayjs('2024-01-20'),
            },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'lecture');

        expect(groups).toHaveLength(2);
        expect(groups[0].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Week 2 - Sun', 'Week 2 - Mon', 'Week 2 - Sat']));
        expect(groups[1].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Week 1 - Sun', 'Week 1 - Mon', 'Week 1 - Sat']));
    });

    it('handles lectures with startDate', () => {
        const items: SidebarCardElement[] = [
            { title: 'Lecture 1', id: 'l1', size: 'M', startDate: dayjs('2024-01-01') },
            { title: 'Lecture 2', id: 'l2', size: 'M', startDate: dayjs('2024-01-08') },
            { title: 'Lecture 3', id: 'l3', size: 'M', startDate: dayjs('2024-01-15') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'lecture');
        expect(groups).toHaveLength(1);
        groups.forEach((g) => expect(g.showDateHeader).toBeFalsy());
    });
});
