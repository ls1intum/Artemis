import SortStrategy from './sortstrategy';
import Comparable from './comparable';

export default class BubbleSort<T extends Comparable = Date> implements SortStrategy<T> {
    /**
     * Sorts objects with BubbleSort.
     *
     * @param input {Array<T>} the array of objects to be sorted
     */
    performSort(input: Array<T>) {
        for (let i = input.length - 1; i >= 0; i--) {
            for (let j = 0; j < i; j++) {
                if (input[j].valueOf() > input[j + 1].valueOf()) {
                    const temp = input[j];
                    input[j] = input[j + 1];
                    input[j + 1] = temp;
                }
            }
        }
    }
}
