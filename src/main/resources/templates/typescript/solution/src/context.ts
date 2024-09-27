import type SortStrategy from './sortstrategy';

export default class Context {
    #sortAlgorithm: SortStrategy | null = null;

    #dates: Date[] = [];

    /**
     * Runs the configured sort algorithm.
     */
    sort() {
        this.#sortAlgorithm?.performSort(this.#dates);
    }

    get sortAlgorithm(): SortStrategy | null {
        return this.#sortAlgorithm;
    }

    set sortAlgorithm(sortAlgorithm: SortStrategy) {
        this.#sortAlgorithm = sortAlgorithm;
    }

    get dates(): Date[] {
        return this.#dates;
    }

    set dates(dates: Date[]) {
        this.#dates = dates;
    }
}
