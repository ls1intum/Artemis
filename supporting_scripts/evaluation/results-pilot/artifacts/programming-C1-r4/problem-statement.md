In this exercise, we want to implement a sorting algorithm and choose it at runtime using the strategy pattern.

### Part 1: Sorting

First, we need to implement the `BubbleSort` algorithm for sorting a list of `Date` objects.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply a sorting algorithm for a `List` of `Date` objects. Use the strategy pattern to select the algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Create a `SortStrategy` interface and adjust the sorting algorithm so that it implements this interface.

2. [task][Context Class](testClass[Context],testMethods[Context])
   Create and implement a `Context` class following the class diagram below. The `Context` holds the list of dates and delegates the sorting to the configured strategy.

3. Complete the `Client` class which demonstrates switching between the strategy at runtime.

@startuml
class Client {
}

class Context {
  <color:testsColor(testClass[Context])>-dates: List<Date></color>
  <color:testsColor(testMethods[Context])>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Date>)</color>
}

MergeSort -up-|> SortStrategy #testsColor(testMergeSort)
BubbleSort -up-|> SortStrategy #testsColor(testBubbleSort)
Context -right-> SortStrategy #testsColor(testClass[Context]): sortAlgorithm
Client .down.> Context
@enduml