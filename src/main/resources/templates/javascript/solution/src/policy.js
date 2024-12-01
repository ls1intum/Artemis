import BubbleSort from './bubblesort.js';
import MergeSort from './mergesort.js';

const DATES_LENGTH_THRESHOLD = 10;

export default class Policy {
    /** @type {Context} */
    #context;

    /**
     * @param context {Context}
     */
    constructor(context) {
        this.#context = context;
    }

    /**
     * Chooses a strategy depending on the number of date objects.
     */
    configure() {
        if (this.#context.dates.length > DATES_LENGTH_THRESHOLD) {
            this.#context.sortAlgorithm = new MergeSort();
        } else {
            this.#context.sortAlgorithm = new BubbleSort();
        }
    }

    get context() {
        return this.#context;
    }

    set context(context) {
        this.#context = context;
    }
}
