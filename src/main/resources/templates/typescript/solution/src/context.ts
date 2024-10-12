import type SortStrategy from './sortstrategy';

export default class Context {
    private _sortAlgorithm: SortStrategy | null = null;

    private _dates: Date[] = [];

    /**
     * Runs the configured sort algorithm.
     */
    sort() {
        this._sortAlgorithm?.performSort(this._dates);
    }

    get sortAlgorithm(): SortStrategy | null {
        return this._sortAlgorithm;
    }

    set sortAlgorithm(sortAlgorithm: SortStrategy) {
        this._sortAlgorithm = sortAlgorithm;
    }

    get dates(): Date[] {
        return this._dates;
    }

    set dates(dates: Date[]) {
        this._dates = dates;
    }
}
