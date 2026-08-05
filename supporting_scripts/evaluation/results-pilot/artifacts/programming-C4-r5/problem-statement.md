In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Color>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Color>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a `List` of `Color` objects.
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testClass[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testClass[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testSelectMergeSort)
    Select `MergeSort` when the List has more than 10 colours.

    2. [task][Select BubbleSort](testSelectBubbleSort)
    Select `BubbleSort` when the List has less or equal 10 colours.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testPolicy)>+configure()</color>
}

class Context {
  <color:testsColor(testContext)>-colors: List<Color></color>
  <color:testsColor(testContextSort)>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testSortStrategy)>+performSort(List<Color>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Color>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Color>)</color>
}

MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
Policy -right-> Context #testsColor(testPolicyContext): context
Context -right-> SortStrategy #testsColor(testContextSortAlgorithm): sortAlgorithm
Client .down.> Policy
Client .down.> Context

hide empty fields
hide empty methods

@enduml

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Color>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.