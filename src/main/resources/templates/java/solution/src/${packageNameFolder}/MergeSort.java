package ${packageName};

import java.util.*;

public class MergeSort implements SortStrategy {

    /**
     * Wrapper method for the real MergeSort algorithm.
     *
     * @param input the List of Dates to be sorted
     */
    public void performSort(List<Date> input) {
        mergesort(input, 0, input.size() - 1);
    }

    /**
     * Recursive merge sort method
     * @oracleIgnore
     */
    private void mergesort(List<Date> input, int low, int high) {
        if (high - low < 1) {
            return;
        }
        int mid = (low + high) / 2;
        mergesort(input, low, mid);
        mergesort(input, mid + 1, high);
        merge(input, low, mid, high);
    }

    /**
     * Merge method
     * @oracleIgnore
     */
    private void merge(List<Date> input, int low, int middle, int high) {

        Date[] temp = new Date[high - low + 1];
        int leftIndex = low;
        int rightIndex = middle + 1;
        int wholeIndex = 0;
        while (leftIndex <= middle && rightIndex <= high) {
            if (input.get(leftIndex).compareTo(input.get(rightIndex)) <= 0) {
                temp[wholeIndex] = input.get(leftIndex++);
            }
            else {
                temp[wholeIndex] = input.get(rightIndex++);
            }
            wholeIndex++;
        }
        if (leftIndex <= middle && rightIndex > high) {
            while (leftIndex <= middle) {
                temp[wholeIndex++] = input.get(leftIndex++);
            }
        }
        else {
            while (rightIndex <= high) {
                temp[wholeIndex++] = input.get(rightIndex++);
            }
        }
        for (wholeIndex = 0; wholeIndex < temp.length; wholeIndex++) {
            input.set(wholeIndex + low, temp[wholeIndex]);
        }
    }
}
