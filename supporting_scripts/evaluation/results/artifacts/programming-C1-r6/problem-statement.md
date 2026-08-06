In this exercise, we want to implement a sorting algorithm and choose it at runtime using the strategy pattern.

### Part 1: Sorting

First, we need to implement the sorting algorithm `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply the sorting algorithm for a `List` of `Date` objects. Use the strategy pattern to select the algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithm so that it implements this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. Complete the `Client` class which demonstrates switching between the strategy at runtime.

@startuml

class Client {
}

class Context {
  <color:testsColor(testAttributes[Context])>-dates: List<Date></color>
  <color:testsColor(testMethods[Context])>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
}

BubbleSort -up-|> SortStrategy #testsColor(testClass[BubbleSort])
Context -right-> SortStrategy #testsColor(testAttributes[Context]): sortAlgorithm
Client .down.> Context

hide empty fields
hide empty methods

@enduml