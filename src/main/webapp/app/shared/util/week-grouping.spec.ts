import { MIN_ITEMS_TO_GROUP_BY_WEEK, WeekGroupingUtil } from './week-grouping.util';
import { SidebarCardElement } from '../types/sidebar';
import dayjs from 'dayjs/esm';

describe('WeekGroupingUtil', () => {
    const createItem = (
        title: string,
        options: {
            dueDate?: string;
            startDateWithTime?: string;
            startDate?: string;
            type?: string;
        } = {},
    ): SidebarCardElement => ({
        title,
        type: options.type,
        id: title,
        size: 'M',
        exercise: options.dueDate ? { dueDate: options.dueDate } : undefined,
        startDateWithTime: options.startDateWithTime ? dayjs(options.startDateWithTime) : undefined,
        startDate: options.startDate ? dayjs(options.startDate) : undefined,
    });

    describe('getGroupedByWeek', () => {
        it('should return a single group without header for special sections', () => {
            const items = [createItem('Exam 1', { dueDate: '2024-01-01' }), createItem('Exam 2', { dueDate: '2024-01-02' })];

            ['real', 'test', 'attempt'].forEach((groupKey) => {
                const result = WeekGroupingUtil.getGroupedByWeek(items, groupKey);
                expect(result).toHaveLength(1);
                expect(result[0].isNoDate).toBeTrue();
                expect(result[0].showDateHeader).toBeFalse();
                expect(result[0].items).toHaveLength(2);
            });
        });

        it('should return a single group without header when searching', () => {
            const items = [createItem('Item 1', { dueDate: '2024-01-01' }), createItem('Item 2', { dueDate: '2024-01-02' }), createItem('Other', { dueDate: '2024-01-03' })];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise', 'item');
            expect(result).toHaveLength(1);
            expect(result[0].isNoDate).toBeTrue();
            expect(result[0].showDateHeader).toBeFalse();
            expect(result[0].items).toHaveLength(2);
        });

        it('should search in both title and type', () => {
            const items = [
                createItem('Normal Item', { dueDate: '2024-01-01', type: 'quiz' }),
                createItem('Quiz Title', { dueDate: '2024-01-02' }),
                createItem('Other', { dueDate: '2024-01-03' }),
            ];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise', 'quiz');
            expect(result).toHaveLength(1);
            expect(result[0].items).toHaveLength(2);
            expect(result[0].items.map((i) => i.title)).toContain('Normal Item');
            expect(result[0].items.map((i) => i.title)).toContain('Quiz Title');
        });

        it('should return a single group without header when items <= MIN_ITEMS_TO_GROUP_BY_WEEK', () => {
            const items = Array.from({ length: MIN_ITEMS_TO_GROUP_BY_WEEK }, (_, i) => createItem(`Item ${i + 1}`, { dueDate: `2024-01-0${i + 1}` }));

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
            expect(result).toHaveLength(1);
            expect(result[0].isNoDate).toBeTrue();
            expect(result[0].showDateHeader).toBeFalse();
            expect(result[0].items).toHaveLength(MIN_ITEMS_TO_GROUP_BY_WEEK);
        });

        it('should group items by week and show headers when items > MIN_ITEMS_TO_GROUP_BY_WEEK', () => {
            const items = [
                ...Array.from({ length: 4 }, (_, i) => createItem(`Week1 Item ${i + 1}`, { dueDate: '2024-01-01' })),
                ...Array.from({ length: 3 }, (_, i) => createItem(`Week2 Item ${i + 1}`, { dueDate: '2024-01-08' })),
            ];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
            expect(result.length).toBeGreaterThan(1);
            result.forEach((group) => {
                expect(group.showDateHeader).toBeTrue();
            });
        });

        it('should prioritize dates correctly', () => {
            const items = [
                createItem('Due Date', {
                    dueDate: '2024-01-01',
                    startDateWithTime: '2024-01-02',
                    startDate: '2024-01-03',
                }),
                createItem('Start Time', {
                    startDateWithTime: '2024-01-02',
                    startDate: '2024-01-03',
                }),
                createItem('Start Date', {
                    startDate: '2024-01-03',
                }),
                createItem('No Date', {}),
            ];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
            expect(result[0].items[0].title).toBe('Due Date');
            expect(result[0].items[1].title).toBe('Start Time');
            expect(result[0].items[2].title).toBe('Start Date');
        });

        it('should sort items within groups by date in descending order', () => {
            const items = [
                createItem('Item 2', { dueDate: '2024-01-02' }),
                createItem('Item 1', { dueDate: '2024-01-01' }),
                createItem('Item 3', { dueDate: '2024-01-03' }),
                createItem('Item 5', { dueDate: '2024-01-05' }),
                createItem('Item 4', { dueDate: '2024-01-04' }),
                createItem('Item 6', { dueDate: '2024-01-06' }),
            ];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');
            const firstGroup = result[0];
            expect(firstGroup.items.map((item) => item.title)).toEqual(['Item 6', 'Item 5', 'Item 4', 'Item 3', 'Item 2', 'Item 1']);
        });

        it('should handle week boundaries correctly', () => {
            const items = [
                createItem('Sunday 1', { dueDate: '2024-01-07' }), // Sunday of week 1
                createItem('Sunday 2', { dueDate: '2024-01-07' }), // Sunday of week 1
                createItem('Monday 1', { dueDate: '2024-01-08' }), // Monday of week 2
                createItem('Monday 2', { dueDate: '2024-01-08' }), // Monday of week 2
                createItem('Saturday 1', { dueDate: '2024-01-13' }), // Saturday of week 2
                createItem('Saturday 2', { dueDate: '2024-01-13' }), // Saturday of week 2
            ];

            const result = WeekGroupingUtil.getGroupedByWeek(items, 'exercise');

            // Items from different ISO weeks should be in different groups
            expect(result[0].start!.isSame(dayjs('2024-01-08'), 'week')).toBeTrue();
            expect(result[1].start!.isSame(dayjs('2024-01-01'), 'week')).toBeTrue();

            // Verify items are in correct groups
            expect(result[0].items.map((i) => i.title)).toContain('Monday 1');
            expect(result[0].items.map((i) => i.title)).toContain('Monday 2');
            expect(result[0].items.map((i) => i.title)).toContain('Saturday 1');
            expect(result[0].items.map((i) => i.title)).toContain('Saturday 2');
            expect(result[1].items.map((i) => i.title)).toContain('Sunday 1');
            expect(result[1].items.map((i) => i.title)).toContain('Sunday 2');
        });
    });
});
