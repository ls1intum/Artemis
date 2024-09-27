export default class MergeSort {
    /**
     * Wrapper method for the real MergeSort algorithm.
     *
     * @param input {Date[]} the array of Dates to be sorted
     */
    performSort(input: Date[]) {
        mergesort(input, 0, input.length - 1);
    }
}

/**
 * Recursive merge sort function
 *
 * @param input {Date[]}
 * @param low {number}
 * @param high {number}
 */
function mergesort(input: Date[], low: number, high: number) {
    if (high - low < 1) {
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
 * @param input {Date[]}
 * @param low {number}
 * @param middle {number}
 * @param high {number}
 */
function merge(input: Date[], low: number, middle: number, high: number) {
    const temp = new Array<Date>(high - low + 1);

    let leftIndex = low;
    let rightIndex = middle + 1;
    let wholeIndex = 0;

    while (leftIndex <= middle && rightIndex <= high) {
        if (input[leftIndex] <= input[rightIndex]) {
            temp[wholeIndex] = input[leftIndex++];
        } else {
            temp[wholeIndex] = input[rightIndex++];
        }
        wholeIndex++;
    }

    if (leftIndex <= middle && rightIndex > high) {
        while (leftIndex <= middle) {
            temp[wholeIndex++] = input[leftIndex++];
        }
    } else {
        while (rightIndex <= high) {
            temp[wholeIndex++] = input[rightIndex++];
        }
    }

    for (wholeIndex = 0; wholeIndex < temp.length; wholeIndex++) {
        input[wholeIndex + low] = temp[wholeIndex];
    }
}
