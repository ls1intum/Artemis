import BubbleSort from './bubblesort';
import MergeSort from './mergesort';
import Context from './context';

const DATES_LENGTH_THRESHOLD = 10;

export default class Policy {
    #context: Context;

    constructor(context: Context) {
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

    get context(): Context {
        return this.#context;
    }

    set context(context: Context) {
        this.#context = context;
    }
}
