import BubbleSort from './bubblesort';
import MergeSort from './mergesort';
import Context from './context';

const DATES_LENGTH_THRESHOLD = 10;

export default class Policy {
    constructor(private _context: Context) {}

    /**
     * Chooses a strategy depending on the number of date objects.
     */
    configure() {
        if (this._context.dates.length > DATES_LENGTH_THRESHOLD) {
            this._context.sortAlgorithm = new MergeSort();
        } else {
            this._context.sortAlgorithm = new BubbleSort();
        }
    }

    get context(): Context {
        return this._context;
    }

    set context(context: Context) {
        this._context = context;
    }
}
