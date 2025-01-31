import 'package:artemis_assignment/bubble_sort.dart';
import 'package:artemis_assignment/merge_sort.dart';
import 'package:artemis_assignment/context.dart';
import 'package:artemis_assignment/policy.dart';
import 'package:test/test.dart';

void main() {
  late List<DateTime> dates;
  late List<DateTime> orderedDates;

  setUp(() {
    var date1 = DateTime(2000);
    var date2 = DateTime(2001);
    var date3 = DateTime(2002);
    var date4 = DateTime(2003);
    dates = [date2, date3, date1, date4];
    orderedDates = [date1, date2, date3, date4];
  });

  test('BubbleSort sorts correctly', () {
    var bubbleSort = BubbleSort();
    bubbleSort.performSort(dates);
    expect(dates, equals(orderedDates),
        reason: "BubbleSort does not sort correctly");
  });

  test('MergeSort sorts correctly', () {
    final mergeSort = MergeSort();
    mergeSort.performSort(dates);
    expect(dates, equals(orderedDates),
        reason: "MergeSort does not sort correctly");
  });

  test('use MergeSort for small list', () {
    final smallList = List.filled(11, DateTime(2000));

    final context = Context()..dates = smallList;
    final policy = Policy(context);

    policy.configure();

    final chosenStrategy = context.sortAlgorithm;

    expect(chosenStrategy, isA<MergeSort>(),
        reason:
            "The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates");
  });

  test('use BubbleSort for small list', () {
    final smallList = List.filled(3, DateTime(2000));

    final context = Context()..dates = smallList;
    final policy = Policy(context);

    policy.configure();

    final chosenStrategy = context.sortAlgorithm;

    expect(chosenStrategy, isA<BubbleSort>(),
        reason:
            "The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates");
  });
}
