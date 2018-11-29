package ${packageName};

import java.util.Date;
import java.util.List;

public class MergeSort implements SortStrategy {

    // Wrapper method for the real algorithm.
	public void performSort(List<Date> input) {
        mergesort(input, 0, input.size() - 1);
    }

    // Recursive mergesort method
    private static void mergesort(List<Date> input, int low, int high) {
        if (high - low < 1) return;
        int mid = (low + high) / 2;
        mergesort(input, low, mid);
        mergesort(input, mid + 1, high);
        merge(input, low, mid, high);
    }

    // Merge method
    private static void merge(List<Date> input, int low, int middle, int high) {

        Object[] tmp = new Object[high - low + 1];
        int leftIndex = low;
        int rightIndex = middle + 1;
        int wholeIndex = 0;
        while (leftIndex <= middle && rightIndex <= high) {
            if (input.get(leftIndex).compareTo(input.get(rightIndex)) <= 0)
                tmp[wholeIndex] = input.get(leftIndex++);
            else
                tmp[wholeIndex] = input.get(rightIndex++);
            wholeIndex++;
        }
        if (leftIndex <= middle && rightIndex > high) {
            while (leftIndex <= middle)
                tmp[wholeIndex++] = input.get(leftIndex++);
        } else {
            while (rightIndex <= high)
                tmp[wholeIndex++] = input.get(rightIndex++);
        }
        for (wholeIndex = 0; wholeIndex < tmp.length; wholeIndex++) {
            input.set(wholeIndex+low, (Date) (tmp[wholeIndex])); // this is the line that would generate the warning
        }
    }
}
