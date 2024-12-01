export default class BubbleSort {
    /**
     * Sorts dates with BubbleSort.
     *
     * @param input {Date[]} the array of Dates to be sorted
     */
    performSort(input) {
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
