In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

3. [task][Handle Null Input in Bubble Sort](testNullInputBubbleSort,testEmptyListBubbleSort)
Ensure that `BubbleSort.performSort` throws an `IllegalArgumentException` when the input list is `null` and completes without error when the list is empty.

4. [task][Handle Null Input in Merge Sort](testNullInputMergeSort,testEmptyListMergeSort)
Ensure that `MergeSort.performSort` throws an `IllegalArgumentException` when the input list is `null` and completes without error when the list is empty.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a `List` of `Date` objects.
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClassSortStrategy,testMethodsSortStrategy)
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testClassContext,testMethodsContext)
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testClassPolicy,testMethodsPolicy)
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testSelectMergeSort,testConfigureMergeSort)
    Select `MergeSort` when the List has more than 10 dates.

    2. [task][Select BubbleSort](testSelectBubbleSort,testConfigureBubbleSort)
    Select `BubbleSort` when the List has less or equal 10 dates.

4. [task][Context Behaviour Without Algorithm](testContextSortWithoutAlgorithm)
Calling `Context.sort()` without a configured strategy must throw an `IllegalStateException`.

5. [task][Policy with Empty List](testPolicyConfigureEmptyList)
When the list is empty, `Policy.configure()` must select `BubbleSort`.

6. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testPolicyConfigureEmptyList)>+configure()</color>
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

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.