export abstract class ChartFilter {
    filterMap: Map<string, boolean> = new Map<string, boolean>();
    numberOfActiveFilters = 0;

    getCurrentFilterState(category: string) {
        return this.filterMap.get(category);
    }
}
