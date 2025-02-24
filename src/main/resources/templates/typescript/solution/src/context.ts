import type SortStrategy from './sortstrategy';

export default class Context {
    private _sortAlgorithm: SortStrategy | null = null;

    private _dates: Array<Date> = [];

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

    get dates(): Array<Date> {
        return this._dates;
    }

    set dates(dates: Array<Date>) {
        this._dates = dates;
    }
}
