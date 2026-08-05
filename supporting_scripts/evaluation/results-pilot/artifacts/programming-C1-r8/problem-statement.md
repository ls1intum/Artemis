In this exercise, we want to implement a sorting algorithm and choose it using the strategy pattern.

### Part 1: Sorting

First, we need to implement a sorting algorithm, in this case `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply a sorting algorithm for a `List` of `Date` objects.
Use the strategy pattern to select the sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface.

2. [task][Context Class](testClass[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

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

BubbleSort -up-|> SortStrategy #testsColor(testBubbleSort)
Client .down.> Context

hide empty fields
hide empty methods
@enduml

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Date>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in a future `Policy` class when to use the new `QuickSort` algorithm.