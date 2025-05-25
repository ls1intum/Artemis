import dayjs from 'dayjs/esm';
import { SidebarCardElement } from 'app/shared/types/sidebar';

export interface WeekGroup {
    isNoDate: boolean;
    start?: dayjs.Dayjs;
    end?: dayjs.Dayjs;
    items: SidebarCardElement[];
    showDateHeader: boolean;
}

export const NO_DATE_KEY = 'artemisApp.courseOverview.sidebar.noDate';
export const MIN_ITEMS_TO_GROUP_BY_WEEK = 5;

export class WeekGroupingUtil {
    /**
     * Extracts the most relevant date from a sidebar element.
     *
     * @param item - The sidebar element to extract the date from
     * @returns The extracted date, or undefined if no date is found
     */
    static getDateFromItem(item: SidebarCardElement): dayjs.Dayjs | undefined {
        const date = item.exercise?.dueDate ?? item.startDateWithTime ?? item.startDate;
        return date ? dayjs(date) : undefined;
    }

    /**
     * Creates a unique key for a week based on its start and end dates.
     *
     * @param date - The date to get the week key for
     * @returns A unique string identifier for the week
     */
    static getWeekKey(date: dayjs.Dayjs): string {
        const startOfWeek = date.startOf('week');
        const endOfWeek = date.endOf('week');
        return `${startOfWeek.year()} - ${startOfWeek.format('DD MMM YYYY')} - ${endOfWeek.format('DD MMM YYYY')}`;
    }

    /**
     * Compares two dates for sorting purposes.
     * Undefined dates are sorted to the end.
     *
     * @param a - First date to compare
     * @param b - Second date to compare
     * @returns Negative if a < b, positive if a > b, 0 if equal
     */
    static compareDates(a?: dayjs.Dayjs, b?: dayjs.Dayjs): number {
        if (!a && !b) {
            return 0;
        }
        if (!a) {
            return 1;
        }
        if (!b) {
            return -1;
        }
        return b.valueOf() - a.valueOf();
    }

    /**
     * Groups sidebar items into ISO‑weeks (or returns them as‑is for special groups).
     *
     * @param items - The items to group
     * @param storageId - Storage identifier for the sidebar
     * @param groupKey - Key of the group being processed
     * @param searchValue - Optional search string to filter items
     * @returns Array of WeekGroup objects containing the grouped items
     */
    static getGroupedByWeek(items: SidebarCardElement[], storageId?: string, groupKey?: string, searchValue = ''): WeekGroup[] {
        // Filter items based on search value if provided
        const filtered = searchValue
            ? items.filter((i) => {
                  const title = i.title?.toLowerCase() ?? '';
                  const type = i.type?.toLowerCase() ?? '';
                  return title.includes(searchValue.toLowerCase()) || type.includes(searchValue.toLowerCase());
              })
            : items;

        // Only apply weekly grouping to exercises and default type
        if ((storageId !== 'exercise' && storageId !== 'lecture') || !!searchValue || filtered.length <= MIN_ITEMS_TO_GROUP_BY_WEEK) {
            return [{ isNoDate: true, items: filtered, showDateHeader: false }];
        }

        // Group items by week
        const weekMap = new Map<string, SidebarCardElement[]>();
        for (const item of filtered) {
            const date = this.getDateFromItem(item);
            const key = date ? this.getWeekKey(date) : NO_DATE_KEY;
            const bucket = weekMap.get(key) ?? [];
            bucket.push(item);
            weekMap.set(key, bucket);
        }

        // Sort items within each week by date
        for (const list of weekMap.values()) {
            list.sort((a, b) => this.compareDates(this.getDateFromItem(a), this.getDateFromItem(b)));
        }

        // Convert week map to WeekGroup array
        const groups: WeekGroup[] = Array.from(weekMap.entries()).map(([key, list]) => {
            if (key === NO_DATE_KEY) {
                return {
                    isNoDate: true,
                    items: list,
                    showDateHeader: groupKey !== 'noDate',
                };
            }

            const [, startStr, endStr] = key.split(' - ');
            return {
                isNoDate: false,
                start: dayjs(startStr, 'DD MMM YYYY'),
                end: dayjs(endStr, 'DD MMM YYYY'),
                items: list,
                showDateHeader: true,
            };
        });

        // Sort groups: dated groups first (by year and date), then no-date groups
        return groups.sort((a, b) => {
            if (a.isNoDate) {
                return 1;
            }
            if (b.isNoDate) {
                return -1;
            }
            if (a.start!.year() !== b.start!.year()) {
                return b.start!.year() - a.start!.year();
            }
            return b.start!.valueOf() - a.start!.valueOf();
        });
    }
}
