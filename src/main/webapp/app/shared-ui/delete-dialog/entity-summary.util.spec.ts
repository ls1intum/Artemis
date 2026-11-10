import { describe, expect, it } from 'vitest';
import {
    LARGE_SUMMARY_COUNT_THRESHOLD,
    hasLargeSummaryCount,
    mergeSummaryCategories,
    summaryCategories,
    summaryCategory,
    summaryItem,
} from 'app/shared-ui/delete-dialog/entity-summary.util';

describe('entity summary util', () => {
    describe('summaryItem', () => {
        it('hides rows without data', () => {
            expect(summaryItem('label', 0)).toBeUndefined();
            expect(summaryItem('label', undefined)).toBeUndefined();
        });

        it('keeps small counts plain', () => {
            expect(summaryItem('label', 3)).toEqual({ labelKey: 'label', value: 3, highlight: false });
            expect(summaryItem('label', LARGE_SUMMARY_COUNT_THRESHOLD - 1)?.highlight).toBe(false);
        });

        it('highlights large counts from the threshold on', () => {
            expect(summaryItem('label', LARGE_SUMMARY_COUNT_THRESHOLD)?.highlight).toBe(true);
            expect(summaryItem('label', 5000)?.highlight).toBe(true);
        });

        it('never highlights counts that are not student work, however large they are', () => {
            expect(summaryItem('builds', 50000, true)).toEqual({ labelKey: 'builds', value: 50000, highlight: false, neverHighlight: true });
        });
    });

    it('keeps rows that are never highlighted quiet when summaries are merged', () => {
        const merged = mergeSummaryCategories([
            [{ titleKey: 'infrastructure', items: [{ labelKey: 'builds', value: 400, highlight: false, neverHighlight: true }] }],
            [{ titleKey: 'infrastructure', items: [{ labelKey: 'builds', value: 400, highlight: false, neverHighlight: true }] }],
        ]);

        expect(hasLargeSummaryCount(merged)).toBe(false);
        expect(merged[0].items[0].value).toBe(800);
    });

    describe('summaryCategory', () => {
        it('drops hidden rows', () => {
            const category = summaryCategory('title', [summaryItem('a', 1), summaryItem('b', 0), summaryItem('c', 2)]);

            expect(category?.items.map((item) => item.labelKey)).toEqual(['a', 'c']);
        });

        it('drops a category without rows', () => {
            expect(summaryCategory('title', [summaryItem('a', 0), undefined])).toBeUndefined();
        });
    });

    it('collects only the categories that exist', () => {
        const kept = summaryCategory('kept', [summaryItem('a', 1)]);

        expect(summaryCategories([undefined, kept, summaryCategory('empty', [])])).toEqual([kept]);
    });

    describe('mergeSummaryCategories', () => {
        it('adds up rows that several entities share and keeps the order of first appearance', () => {
            const first = [
                { titleKey: 'work', items: [{ labelKey: 'participations', value: 300 }] },
                { titleKey: 'chat', items: [{ labelKey: 'posts', value: 2 }] },
            ];
            const second = [
                {
                    titleKey: 'work',
                    items: [
                        { labelKey: 'participations', value: 250 },
                        { labelKey: 'submissions', value: 5 },
                    ],
                },
            ];

            const merged = mergeSummaryCategories([first, second]);

            expect(merged.map((category) => category.titleKey)).toEqual(['work', 'chat']);
            expect(merged[0].items).toEqual([
                { labelKey: 'participations', value: 550, highlight: true },
                { labelKey: 'submissions', value: 5, highlight: false },
            ]);
        });

        it('returns nothing when there is nothing to merge', () => {
            expect(mergeSummaryCategories([])).toEqual([]);
        });
    });

    describe('hasLargeSummaryCount', () => {
        it('is true when a row is highlighted', () => {
            expect(hasLargeSummaryCount([{ titleKey: 't', items: [{ labelKey: 'a', value: 500, highlight: true }] }])).toBe(true);
        });

        it('is false without highlighted rows or summary', () => {
            expect(hasLargeSummaryCount([{ titleKey: 't', items: [{ labelKey: 'a', value: 5 }] }])).toBe(false);
            expect(hasLargeSummaryCount(undefined)).toBe(false);
        });
    });
});
