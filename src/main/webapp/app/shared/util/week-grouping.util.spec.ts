import { MIN_ITEMS_TO_GROUP_BY_WEEK, WeekGroupingUtil } from './week-grouping.util';
import { SidebarCardElement } from '../types/sidebar';
import dayjs from 'dayjs/esm';

describe('WeekGroupingUtil • getGroupedByWeek', () => {
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
            {
                title: 'Item 1',
                id: 'i1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-01'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Item 2',
                id: 'i2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-02'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Other',
                id: 'i3',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-03'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'exercise', 'item');
        expect(groups).toHaveLength(1);
        expect(groups[0].showDateHeader).toBeFalsy();
        expect(groups[0].items).toHaveLength(2);
        expect(groups[0].items.map((i) => i.title)).toEqual(['Item 1', 'Item 2']);
    });

    it('searches in both title and type', () => {
        const items: SidebarCardElement[] = [
            {
                title: 'Normal Item',
                id: 'n1',
                size: 'M',
                type: 'quiz',
                exercise: {
                    dueDate: dayjs('2024-01-01'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Quiz Title',
                id: 'n2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-02'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Other',
                id: 'n3',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-03'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'exercise', 'quiz');
        expect(groups).toHaveLength(1);
        expect(groups[0].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Normal Item', 'Quiz Title']));
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
                title: 'Sun 1',
                id: 's1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-07'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Sun 2',
                id: 's2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-07'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Mon 1',
                id: 'm1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-08'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Mon 2',
                id: 'm2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-08'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Sat 1',
                id: 't1',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-13'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
            {
                title: 'Sat 2',
                id: 't2',
                size: 'M',
                exercise: {
                    dueDate: dayjs('2024-01-13'),
                    numberOfAssessmentsOfCorrectionRounds: [],
                    studentAssignedTeamIdComputed: false,
                    secondCorrectionEnabled: false,
                },
            },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');

        // first group is ISO week beginning 2024-01-08
        expect(groups[0].start!.isSame(dayjs('2024-01-08'), 'week')).toBeTruthy();
        expect(groups[1].start!.isSame(dayjs('2024-01-01'), 'week')).toBeTruthy();

        expect(groups[0].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Mon 1', 'Mon 2', 'Sat 1', 'Sat 2']));
        expect(groups[1].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Sun 1', 'Sun 2']));
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
