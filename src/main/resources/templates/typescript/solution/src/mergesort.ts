import SortStrategy from './sortstrategy';
import Comparable from './comparable';

export default class MergeSort<T extends Comparable> implements SortStrategy<T> {
    /**
     * Wrapper method for the real MergeSort algorithm.
     *
     * @template T
     * @param input {Array<T>} the array of objects to be sorted
     */
    performSort(input: Array<T>) {
        mergesort(input, 0, input.length - 1);
    }
}

/**
 * Recursive merge sort function
 *
 * @template T
 * @param input {Array<T>}
 * @param low {number}
 * @param high {number}
 */
function mergesort<T extends Comparable>(input: Array<T>, low: number, high: number) {
    if (low >= high) {
        return;
    }
    const mid = Math.floor((low + high) / 2);
    mergesort(input, low, mid);
    mergesort(input, mid + 1, high);
    merge(input, low, mid, high);
}

/**
 * Merge function
 *
 * @template T
 * @param input {Array<T>}
 * @param low {number}
 * @param middle {number}
 * @param high {number}
 */
function merge<T extends Comparable>(input: Array<T>, low: number, middle: number, high: number) {
    const temp = new Array<T>(high - low + 1);

    let leftIndex = low;
    let rightIndex = middle + 1;
    let wholeIndex = 0;

    while (leftIndex <= middle && rightIndex <= high) {
        if (input[leftIndex].valueOf() <= input[rightIndex].valueOf()) {
            temp[wholeIndex] = input[leftIndex++];
        } else {
            temp[wholeIndex] = input[rightIndex++];
        }
        wholeIndex++;
    }

    while (leftIndex <= middle) {
        temp[wholeIndex++] = input[leftIndex++];
    }
    while (rightIndex <= high) {
        temp[wholeIndex++] = input[rightIndex++];
    }

    for (wholeIndex = 0; wholeIndex < temp.length; wholeIndex++) {
        input[wholeIndex + low] = temp[wholeIndex];
    }
}
