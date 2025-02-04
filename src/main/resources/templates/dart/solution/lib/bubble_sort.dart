import 'sort_strategy.dart';

class BubbleSort implements SortStrategy {
  /// Sorts dates with BubbleSort.
  @override
  void performSort(List<DateTime> input) {
    for (var i = input.length - 1; i >= 0; i--) {
      for (var j = 0; j < i; j++) {
        if (input[j].compareTo(input[j + 1]) > 0) {
          final temp = input[j];
          input[j] = input[j + 1];
          input[j + 1] = temp;
        }
      }
    }
  }
}
