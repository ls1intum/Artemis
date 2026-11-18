import 'sort_strategy.dart';

class MergeSort implements SortStrategy {
  /// Wrapper method for the real MergeSort algorithm.
  @override
  void performSort(List<DateTime> input) {
    mergesort(input, 0, input.length);
  }

  /// Recursive merge sort method
  void mergesort(List<DateTime> input, int low, int high) {
    if (high - low <= 1) return;

    final mid = (low + high) ~/ 2;

    mergesort(input, low, mid);
    mergesort(input, mid, high);
    merge(input, low, mid, high);
  }

  /// Merge method
  void merge(List<DateTime> input, int low, int middle, int high) {
    final List<DateTime?> temp = List.filled(high - low, null);

    var leftIndex = low;
    var rightIndex = middle;
    var wholeIndex = 0;

    while (leftIndex < middle && rightIndex < high) {
      if (input[leftIndex].compareTo(input[rightIndex]) <= 0) {
        temp[wholeIndex] = input[leftIndex];
        leftIndex += 1;
      } else {
        temp[wholeIndex] = input[rightIndex];
        rightIndex += 1;
      }
      wholeIndex += 1;
    }

    while (leftIndex < middle) {
      temp[wholeIndex] = input[leftIndex];
      leftIndex += 1;
      wholeIndex += 1;
    }

    while (rightIndex < high) {
      temp[wholeIndex] = input[rightIndex];
      rightIndex += 1;
      wholeIndex += 1;
    }

    for (int i = 0; i < temp.length; i++) {
      input[low + i] = temp[i]!;
    }
  }
}
