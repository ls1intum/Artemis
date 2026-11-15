import { EntitySummaryCategory, EntitySummaryItem } from 'app/shared-ui/delete-dialog/delete-dialog.model';

/**
 * A single count of student work (participations, submissions, results, posts, ...) in a deletion summary is emphasised from this value on.
 * Together with the warning in the delete dialogs this keeps instructors from deleting an exercise or exam with a lot of student work by
 * accident. It is high enough that an ordinary exercise of a small course does not trigger it.
 */
export const LARGE_SUMMARY_COUNT_THRESHOLD = 500;

/**
 * Creates a summary row for a count, or nothing when there is nothing to delete, so the dialog only lists what is really affected.
 *
 * @param labelKey translation key of the row
 * @param value the count
 * @param neverHighlight set for counts that are not student work, such as builds or registered students, which are large for almost every
 *                       programming exercise or real exam and would make the warning meaningless
 */
export function summaryItem(labelKey: string, value: number | undefined, neverHighlight = false): EntitySummaryItem | undefined {
    if (!value) {
        return undefined;
    }
    return neverHighlight ? { labelKey, value, highlight: false, neverHighlight } : { labelKey, value, highlight: value >= LARGE_SUMMARY_COUNT_THRESHOLD };
}

/**
 * Creates a summary category from the given rows and drops it entirely when all of its rows are hidden.
 */
export function summaryCategory(titleKey: string, items: (EntitySummaryItem | undefined)[]): EntitySummaryCategory | undefined {
    const visibleItems = items.filter((item): item is EntitySummaryItem => item !== undefined);
    return visibleItems.length > 0 ? { titleKey, items: visibleItems } : undefined;
}

/**
 * Collects the categories that have rows.
 */
export function summaryCategories(categories: (EntitySummaryCategory | undefined)[]): EntitySummaryCategory[] {
    return categories.filter((category): category is EntitySummaryCategory => category !== undefined);
}

/**
 * Adds up the summaries of several entities that are deleted together, so the dialog shows the total per row.
 */
export function mergeSummaryCategories(summaries: EntitySummaryCategory[][]): EntitySummaryCategory[] {
    const totals = new Map<string, Map<string, number>>();
    const neverHighlighted = new Set<string>();
    for (const categories of summaries) {
        for (const category of categories) {
            const items = totals.get(category.titleKey) ?? new Map<string, number>();
            for (const item of category.items) {
                items.set(item.labelKey, (items.get(item.labelKey) ?? 0) + (typeof item.value === 'number' ? item.value : 0));
                if (item.neverHighlight) {
                    neverHighlighted.add(item.labelKey);
                }
            }
            totals.set(category.titleKey, items);
        }
    }
    return summaryCategories(
        Array.from(totals.entries()).map(([titleKey, items]) =>
            summaryCategory(
                titleKey,
                Array.from(items.entries()).map(([labelKey, value]) => summaryItem(labelKey, value, neverHighlighted.has(labelKey))),
            ),
        ),
    );
}

/**
 * Whether the dialog should warn about the amount of data that is about to be deleted.
 */
export function hasLargeSummaryCount(categories: EntitySummaryCategory[] | undefined): boolean {
    return !!categories?.some((category) => category.items.some((item) => item.highlight));
}
