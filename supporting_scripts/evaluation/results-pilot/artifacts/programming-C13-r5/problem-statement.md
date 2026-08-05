In this whimsical workshop, a master painter wishes to arrange their colour palette efficiently. Depending on how many pigments are present, the painter will choose the most suitable sorting technique.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`, that can order a list of **colours**.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Colour>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Colour>)` in the class `MergeSort`. Follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

The painter wants the application to apply different algorithms for sorting a `List` of `Colour` objects. Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testClass[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testClass[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testSelectMergeSort,testConfigureMergeSort)
    Select `MergeSort` when the list has more than 10 colours.

    2. [task][Select BubbleSort](testSelectBubbleSort,testConfigureBubbleSort)
    Select `BubbleSort` when the list has less or equal 10 colours.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testConfigureMergeSort)>+configure()</color>
}

class Context {
  <color:testsColor(testClass[Context])>-colours: List<Colour></color>
  <color:testsColor(testMethods[Context])>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethods[SortStrategy])>+performSort(List<Colour>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Colour>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Colour>)</color>
}

MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
Policy -right-> Context #testsColor(testClass[Policy]): context
Context -right-> SortStrategy #testsColor(testClass[Context]): sortAlgorithm
Client .down.> Policy
Client .down.> Context

hide empty fields
hide empty methods

@enduml

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Colour>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.