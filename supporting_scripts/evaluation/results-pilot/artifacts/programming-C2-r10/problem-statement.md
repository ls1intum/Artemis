In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a `List` of `Date` objects.
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClassSortStrategy,testMethodsSortStrategy)
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testClassContext,testMethodsContext)
Create and implement a `Context` class following the below class diagram

3. [task][Context Policy](testClassPolicy,testMethodsPolicy,testMethodsConfigure)
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testSelectMergeSort,testConfigureMergeSort)
    Select `MergeSort` when the List has more than 10 dates.

    2. [task][Select BubbleSort](testSelectBubbleSort,testConfigureBubbleSort)
    Select `BubbleSort` when the List has less or equal 10 dates.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

### Hard Requirements (additional for HARD difficulty)

The following edge‑case handling and stability requirements are now part of the grading:

- The sorting algorithms must gracefully handle a `null` input list (no `NullPointerException`). If the input is `null`, the list should remain unchanged.
- The sorting algorithms must correctly handle an empty list.
- The sorting algorithms must be **stable**: for equal dates the original relative order must be preserved.
- The `Policy.configure()` method must correctly handle the boundary case where the list size is exactly the threshold (10) and must also handle a `null` or empty list without throwing exceptions.

**You have the following additional tasks:**

1. [task][Policy Edge Cases](testConfigureExactlyThreshold,testConfigureNullDates,testConfigureEmptyList)
Ensure `Policy.configure()` behaves correctly for a list size exactly equal to the threshold, for a `null` list, and for an empty list.

2. [task][Sorting Edge Cases](testBubbleSortNullInput,testMergeSortNullInput,testBubbleSortEmptyList,testMergeSortEmptyList,testBubbleSortStability,testMergeSortStability)
Make sure both `BubbleSort` and `MergeSort` handle `null` and empty inputs without errors and preserve stability for equal dates.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testConfigureMergeSort)>+configure()</color>
}

class Context {
  <color:testsColor(testClassContext)>-dates: List<Date></color>
  <color:testsColor(testMethodsContext)>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethodsSortStrategy)>+performSort(List<Date>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Date>)</color>
}

MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
Policy -right-> Context #testsColor(testClassPolicy): context
Context -right-> SortStrategy #testsColor(testClassContext): sortAlgorithm
Client .down.> Policy
Client .down.> Context

hide empty fields
hide empty methods

@enduml