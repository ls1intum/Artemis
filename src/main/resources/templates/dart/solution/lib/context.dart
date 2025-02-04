import 'sort_strategy.dart';

class Context {
  List<DateTime>? dates;
  SortStrategy? sortAlgorithm;

  /// Runs the configured sort algorithm.
  void sort() {
    final dates = this.dates;
    if (dates == null) {
      throw StateError('dates has not been set');
    }

    final sortAlgorithm = this.sortAlgorithm;
    if (sortAlgorithm == null) {
      throw StateError('sortStrategy has not been set');
    }

    sortAlgorithm.performSort(dates);
  }
}
