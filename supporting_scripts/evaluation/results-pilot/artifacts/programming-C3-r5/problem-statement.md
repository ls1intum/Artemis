In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables. The domain is a library that tracks returned books.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<ReturnedBook>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<ReturnedBook>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a `List` of `ReturnedBook` objects. Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testSortStrategyInterface,testSortStrategyMethods)
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testContextClass,testContextMethods)
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testPolicyClass,testPolicyMethods,testPolicyConfigure)
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testSelectMergeSort,testPolicyConfigureMerge)
    Select `MergeSort` when the list has more than 10 returned books.

    2. [task][Select BubbleSort](testSelectBubbleSort,testPolicyConfigureBubble)
    Select `BubbleSort` when the list has less or equal 10 returned books.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testPolicyConfigure)>+configure()</color>
}

class Context {
  <color:testsColor(testContextClass)>-returnedBooks: List<ReturnedBook></color>
  <color:testsColor(testContextMethods)>+sort()</color>
  <color:testsColor(testContextMethods)>+getReturnedBooks(): List<ReturnedBook></color>
  <color:testsColor(testContextMethods)>+setReturnedBooks(List<ReturnedBook>)</color>
}

interface SortStrategy {
  <color:testsColor(testSortStrategyMethods)>+performSort(List<ReturnedBook>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<ReturnedBook>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<ReturnedBook>)</color>
}

MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
Policy -right-> Context #testsColor(testPolicyClass): context
Context -right-> SortStrategy #testsColor(testContextClass): sortAlgorithm
Client .down.> Policy
Client .down.> Context

hide empty fields
hide empty methods

@enduml

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<ReturnedBook>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.