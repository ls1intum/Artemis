import 'package:artemis_assignment/bubble_sort.dart';
import 'package:artemis_assignment/context.dart';
import 'package:artemis_assignment/merge_sort.dart';

class Policy {
  static const int mergeSortThreshold = 10;

  final Context context;

  Policy(this.context);

  /// Chooses a strategy depending on the number of date objects.
  void configure() {
    final dates = context.dates;
    if (dates == null) {
      throw StateError(
          'dates of Context has to be set before configure() can be called');
    }

    if (dates.length >= mergeSortThreshold) {
      context.sortAlgorithm = MergeSort();
    } else {
      context.sortAlgorithm = BubbleSort();
    }
  }
}
