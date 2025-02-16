import 'dart:mirrors';

import 'package:${packageName}/bubble_sort.dart';
import 'package:${packageName}/merge_sort.dart';
import 'package:${packageName}/context.dart';
import 'package:${packageName}/policy.dart';
import 'package:test/test.dart';

void main() {
  late List<DateTime> dates;
  late List<DateTime> orderedDates;

  setUp(() {
    var date1 = DateTime(2018, 11, 8);
    var date2 = DateTime(2017, 4, 15);
    var date3 = DateTime(2016, 2, 15);
    var date4 = DateTime(2017, 9, 15);
    dates = [date1, date2, date3, date4];
    orderedDates = [date3, date2, date4, date1];
  });

  test('BubbleSort_sorts_correctly', () {
    var bubbleSort = BubbleSort();
    bubbleSort.performSort(dates);
    expect(dates, equals(orderedDates),
        reason: "BubbleSort does not sort correctly");
  });

  test('MergeSort_sorts_correctly', () {
    final mergeSort = MergeSort();
    mergeSort.performSort(dates);
    expect(dates, equals(orderedDates),
        reason: "MergeSort does not sort correctly");
  });

  test('use_MergeSort_for_big_list', () {
    final bigList = List.filled(11, DateTime(2000));

    final chosenStrategy = configurePolicyAndContext(bigList);

    expect(chosenStrategy, isA<MergeSort>(),
        reason:
            "The sort algorithm of Context was not MergeSort for a list with more than 10 dates");
  });

  test('use_BubbleSort_for_small_list', () {
    final smallList = List.filled(3, DateTime(2000));

    final chosenStrategy = configurePolicyAndContext(smallList);

    expect(chosenStrategy, isA<BubbleSort>(),
        reason:
            "The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates");
  });
}

Object configurePolicyAndContext(List<DateTime> dates) {
  final contextClass = reflectClass(Context);
  final context = contextClass.newInstance(Symbol.empty, []);
  context.setField(#dates, dates);

  final policyClass = reflectClass(Policy);
  final policy = policyClass.newInstance(Symbol.empty, [context.reflectee]);
  policy.invoke(#configure, []);

  return context.getField(#sortAlgorithm).reflectee;
}
