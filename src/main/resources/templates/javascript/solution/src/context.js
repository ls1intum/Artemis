export default class Context {
    /** @type {?{ performSort: (input: Date[]) => void }} */
    #sortAlgorithm = null;

    /** @type {Date[]} */
    #dates = [];

    /**
     * Runs the configured sort algorithm.
     */
    sort() {
        this.#sortAlgorithm?.performSort(this.#dates);
    }

    get sortAlgorithm() {
        return this.#sortAlgorithm;
    }

    set sortAlgorithm(sortAlgorithm) {
        this.#sortAlgorithm = sortAlgorithm;
    }

    get dates() {
        return this.#dates;
    }

    set dates(dates) {
        this.#dates = dates;
    }
}
