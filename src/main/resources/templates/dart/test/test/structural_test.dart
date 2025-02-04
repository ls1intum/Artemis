import 'package:${packageName}/bubble_sort.dart';
import 'package:${packageName}/merge_sort.dart';
import 'package:${packageName}/context.dart';
import 'package:${packageName}/policy.dart';
import 'package:${packageName}/sort_strategy.dart';
import 'package:test/test.dart';
import 'dart:mirrors';

void main() {
  test('MergeSort_implements_SortStrategy', () {
    final mergeSort = reflectClass(MergeSort);
    final sortStrategy = reflectClass(SortStrategy);

    expect(mergeSort.superinterfaces, contains(sortStrategy));
  });

  test('BubbleSort_implements_SortStrategy', () {
    final bubbleSort = reflectClass(BubbleSort);
    final sortStrategy = reflectClass(SortStrategy);

    expect(bubbleSort.superinterfaces, contains(sortStrategy));
  });

  test('SortStrategy_class', () {
    final sortStrategy = reflectClass(SortStrategy);

    expect(sortStrategy.isAbstract, isTrue,
        reason: 'SortStrategy is not abstract');
  });

  test('SortStrategy_methods', () {
    final sortStrategy = reflectClass(SortStrategy);

    var performSort = sortStrategy.declarations[#performSort];
    expect(performSort, isA<MethodMirror>(),
        reason: 'SortStrategy has no method performSort');
    performSort = performSort as MethodMirror;
    expect(performSort.parameters.length, equals(1),
        reason: 'performSort should have exactly one parameter');
    final parameterType = performSort.parameters.first.type;
    expect(parameterType.isAssignableTo(reflectType(List<Comparable>)), isTrue,
        reason: 'performSort\'s parameter has an unexpected type');
    expect(performSort.isAbstract, isTrue,
        reason: 'performSort should be abstract');
  });

  test('Context_accessors', () {
    final context = reflectClass(Context);

    final datesGetter = context.instanceMembers[#dates];
    expect(datesGetter?.isGetter, isTrue,
        reason: "Context has no getter for 'dates'");
    expect(datesGetter!.returnType.reflectedType, equals(List<DateTime>),
        reason: "The 'dates' getter of Context has the wrong type");

    final datesSetter = context.instanceMembers[Symbol('dates=')];
    expect(datesSetter?.isSetter, isTrue,
        reason: "Context has no setter for 'dates'");
    expect(datesSetter!.parameters.first.type.reflectedType,
        equals(List<DateTime>),
        reason: "The 'dates' setter of Context has the wrong type");

    final sortAlgorithmGetter = context.instanceMembers[#sortAlgorithm];
    expect(sortAlgorithmGetter?.isGetter, isTrue,
        reason: "Context has no getter for 'sortAlgorithm'");
    expect(sortAlgorithmGetter!.returnType.reflectedType, equals(SortStrategy),
        reason: "The 'sortAlgorithm' getter of Context has the wrong type");

    final sortAlgorithmSetter =
        context.instanceMembers[Symbol('sortAlgorithm=')];
    expect(sortAlgorithmSetter?.isSetter, isTrue,
        reason: "Context has no setter for 'sortAlgorithm'");
    expect(sortAlgorithmSetter!.parameters.first.type.reflectedType,
        equals(SortStrategy),
        reason: "The 'sortAlgorithm' setter of Context has the wrong type");
  });

  test('Context_methods', () {
    final context = reflectClass(Context);

    var sort = context.instanceMembers[#sort];
    expect(sort, isNotNull, reason: 'Context has no method \'sort\'');
    expect(sort!.parameters, isEmpty,
        reason: "The 'sort' method of Context should have no parameters");
  });

  test('Policy_accessors', () {
    final policy = reflectClass(Policy);

    final contextGetter = policy.instanceMembers[#context];
    expect(contextGetter?.isGetter, isTrue,
        reason: "Policy has no getter for 'context'");
    expect(contextGetter!.returnType.reflectedType, equals(Context),
        reason: "The 'context' getter of Policy has the wrong type");
  });

  test('Policy_constructor', () {
    final policy = reflectClass(Policy);

    var constructor = policy.declarations[#Policy] as MethodMirror;
    expect(constructor.parameters.length, equals(1),
        reason: 'The constructor should have exactly one parameter');
    final parameterType = constructor.parameters.first.type;
    expect(parameterType.reflectedType, equals(Context),
        reason: 'The constructor\'s parameter has the wrong type');
  });

  test('Policy_methods', () {
    final policy = reflectClass(Policy);

    var context = policy.instanceMembers[#configure];
    expect(context, isNotNull, reason: 'Policy has no method \'configure\'');
    expect(context!.parameters, isEmpty,
        reason: "The 'configure' method of Policy should have no parameters");
  });
}
