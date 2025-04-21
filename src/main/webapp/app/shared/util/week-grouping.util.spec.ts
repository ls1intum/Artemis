import { WeekGroupingUtil } from './week-grouping.util';
import { SidebarCardElement } from '../types/sidebar';
import dayjs from 'dayjs/esm';

describe('WeekGroupingUtil', () => {
    it('returns a single group for noDate', () => {
        const items: SidebarCardElement[] = [
            { title: 'Item 1', id: 'i1', size: 'M' },
            { title: 'Item 2', id: 'i2', size: 'M' },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'noDate');
        expect(groups).toHaveLength(1);
        expect(groups[0].isNoDate).toBeTruthy();
        expect(groups[0].showDateHeader).toBeFalsy();
        expect(groups[0].items).toHaveLength(2);
    });

    it('returns a single group while searching (no headers)', () => {
        const items: SidebarCardElement[] = [
            { title: 'Item 1', id: 'i1', size: 'M', startDate: dayjs('2024-01-01') },
            { title: 'Item 2', id: 'i2', size: 'M', startDate: dayjs('2024-01-02') },
            { title: 'Other', id: 'i3', size: 'M', startDate: dayjs('2024-01-03') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'current', 'item');
        expect(groups).toHaveLength(1);
        expect(groups[0].showDateHeader).toBeFalsy();
        expect(groups[0].items).toHaveLength(2);
        expect(groups[0].items.map((i) => i.title)).toEqual(['Item 1', 'Item 2']);
    });

    it('searches in both title and type', () => {
        const items: SidebarCardElement[] = [
            { title: 'Exercise 1', id: 'e1', size: 'M', type: 'exercise', startDate: dayjs('2024-01-01') },
            { title: 'Exercise 2', id: 'e2', size: 'M', startDate: dayjs('2024-01-02') },
            { title: 'Exercise 3', id: 'e3', size: 'M', startDate: dayjs('2024-01-03') },
            { title: 'Exercise 4', id: 'e4', size: 'M', startDate: dayjs('2024-01-04') },
            { title: 'Exercise 5', id: 'e5', size: 'M', startDate: dayjs('2024-01-05') },
            { title: 'Exercise 6', id: 'e6', size: 'M', startDate: dayjs('2024-01-07') },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'current', 'exercise');
        expect(groups).toHaveLength(1);
        expect(groups[0].items.map((i) => i.title)).toEqual(expect.arrayContaining(['Exercise 1', 'Exercise 2', 'Exercise 3', 'Exercise 4', 'Exercise 5', 'Exercise 6']));
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

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'current');
        expect(groups).toHaveLength(2);

        // First group should be Week 2 (Jan 8-14)
        expect(groups[0].start!.format('DD MMM YYYY')).toBe('07 Jan 2024');
        expect(groups[0].end!.format('DD MMM YYYY')).toBe('13 Jan 2024');

        // Second group should be Week 1 (Jan 1-7)
        expect(groups[1].start!.format('DD MMM YYYY')).toBe('31 Dec 2023');
        expect(groups[1].end!.format('DD MMM YYYY')).toBe('06 Jan 2024');
    });

    it('sorts items inside each group by date (descending)', () => {
        const items: SidebarCardElement[] = ['06', '05', '04', '03', '02', '01'].map((d) => ({
            title: `Item ${d}`,
            id: `d${d}`,
            size: 'M',
            startDate: dayjs(`2024-01-${d}`),
        }));

        const [firstGroup] = WeekGroupingUtil.getGroupedByWeek(items, 'current');
        expect(firstGroup.items.map((i) => i.title)).toEqual(['Item 06', 'Item 05', 'Item 04', 'Item 03', 'Item 02', 'Item 01']);
    });

    it('handles different group keys correctly', () => {
        const currentItems: SidebarCardElement[] = [
            { title: 'Current 1', id: 'c1', size: 'M', startDate: dayjs() },
            { title: 'Current 2', id: 'c2', size: 'M', startDate: dayjs().add(1, 'day') },
            { title: 'Current 3', id: 'c3', size: 'M', startDate: dayjs().add(1, 'day') },
            { title: 'Current 4', id: 'c4', size: 'M', startDate: dayjs().add(1, 'day') },
            { title: 'Current 5', id: 'c5', size: 'M', startDate: dayjs().add(1, 'day') },
            { title: 'Current 6', id: 'c6', size: 'M', startDate: dayjs().add(1, 'day') },
        ];

        const futureItems: SidebarCardElement[] = [
            { title: 'Future 1', id: 'f1', size: 'M', startDate: dayjs().add(1, 'month') },
            { title: 'Future 2', id: 'f2', size: 'M', startDate: dayjs().add(2, 'month') },
        ];

        const noDateItems: SidebarCardElement[] = [
            { title: 'No Date 1', id: 'n1', size: 'M' },
            { title: 'No Date 2', id: 'n2', size: 'M' },
        ];

        const currentGroups = WeekGroupingUtil.getGroupedByWeek(currentItems, 'current');
        expect(currentGroups).toHaveLength(1);
        expect(currentGroups[0].showDateHeader).toBeTruthy();

        const futureGroups = WeekGroupingUtil.getGroupedByWeek(futureItems, 'future');
        expect(futureGroups).toHaveLength(1);
        expect(futureGroups[0].showDateHeader).toBeFalsy();

        const noDateGroups = WeekGroupingUtil.getGroupedByWeek(noDateItems, 'noDate');
        expect(noDateGroups).toHaveLength(1);
        expect(noDateGroups[0].isNoDate).toBeTruthy();
        expect(noDateGroups[0].showDateHeader).toBeFalsy();
    });

    it('handles mixed dates within each group key', () => {
        const items: SidebarCardElement[] = [
            { title: 'Week 1', id: 'c1', size: 'M', startDate: dayjs() },
            { title: 'Week 2', id: 'c2', size: 'M', startDate: dayjs() },
            { title: 'Week 3', id: 'c3', size: 'M', startDate: dayjs() },
            { title: 'Week 4', id: 'c4', size: 'M', startDate: dayjs() },
            { title: 'Week 5', id: 'c5', size: 'M', startDate: dayjs() },
            { title: 'Week 6', id: 'c6', size: 'M', startDate: dayjs() },
            { title: 'Week 7', id: 'c7', size: 'M' },
            { title: 'Week 8', id: 'c8', size: 'M' },
            { title: 'Week 9', id: 'c9', size: 'M' },
        ];

        const currentGroups = WeekGroupingUtil.getGroupedByWeek(items, 'future');
        expect(currentGroups).toHaveLength(2);

        expect(currentGroups[0].showDateHeader).toBeTruthy();
        expect(currentGroups[0].isNoDate).toBeFalsy();
        expect(currentGroups[0].items[0].title).toBe('Week 1');

        expect(currentGroups[1].showDateHeader).toBeTruthy();
        expect(currentGroups[1].isNoDate).toBeTruthy();
        expect(currentGroups[1].items[0].title).toBe('Week 7');
    });

    it('sorts groups correctly within the same year', () => {
        const items: SidebarCardElement[] = [
            // March items
            { title: 'March 1', id: 'm1', size: 'M', startDate: dayjs('2025-03-01') },
            { title: 'March 15', id: 'm2', size: 'M', startDate: dayjs('2025-03-15') },
            // January items
            { title: 'January 1', id: 'j1', size: 'M', startDate: dayjs('2025-01-01') },
            { title: 'January 15', id: 'j2', size: 'M', startDate: dayjs('2025-01-15') },
            // February items
            { title: 'February 1', id: 'f1', size: 'M', startDate: dayjs('2025-02-01') },
            { title: 'February 15', id: 'f2', size: 'M', startDate: dayjs('2025-02-15') },
            // No date items
            { title: 'No Date 1', id: 'n1', size: 'M' },
            { title: 'No Date 2', id: 'n2', size: 'M' },
        ];

        const groups = WeekGroupingUtil.getGroupedByWeek(items, 'current');

        // Should have 7 groups (2 weeks per month for 3 months + 1 no-date group)
        expect(groups).toHaveLength(7);

        // Groups should be ordered by date (newest first) with no-date group at the end
        expect(groups[0].start!.format('YYYY-MM-DD')).toBe('2025-03-09'); // March week 2
        expect(groups[1].start!.format('YYYY-MM-DD')).toBe('2025-02-23'); // March week 1
        expect(groups[2].start!.format('YYYY-MM-DD')).toBe('2025-02-09'); // February week 2
        expect(groups[3].start!.format('YYYY-MM-DD')).toBe('2025-01-26'); // February week 1
        expect(groups[4].start!.format('YYYY-MM-DD')).toBe('2025-01-12'); // January week 2
        expect(groups[5].start!.format('YYYY-MM-DD')).toBe('2024-12-29'); // January week 1
        expect(groups[6].isNoDate).toBeTruthy();
    });
});
