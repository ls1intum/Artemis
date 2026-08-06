In this exercise, you are a master painter who needs to organise a chaotic colour palette before starting a masterpiece. Each colour is represented by a hue value, and the palette must be sorted by hue so that the painter can pick the right shade at a glance.

### Part 1: Sorting

First, you need to implement two sorting algorithms that can order a list of `Colour` objects: `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Colour>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Colour>)` in the class `MergeSort`. Follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

The painting studio should automatically pick the most efficient sorting algorithm based on the size of the colour palette. Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the palette contains more than 10 colours.

    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the palette contains 10 or fewer colours.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testMethods[Policy])>+configure()</color>
}

class Context {
  <color:testsColor(testAttributes[Context])>-colours: List<Colour></color>
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

MergeSort -up-|> SortStrategy #testsColor(testClass[MergeSort])
BubbleSort -up-|> SortStrategy #testsColor(testClass[BubbleSort])
Policy -right-> Context #testsColor(testAttributes[Policy]): context
Context -right-> SortStrategy #testsColor(testAttributes[Context]): sortAlgorithm
Client .down.> Policy
Client .down.> Context

hide empty fields
hide empty methods

@enduml

### Part 3: Optional Challenges (Hard)

These challenges are not covered by the automated tests but will push your implementation to the limits of a seasoned painter:

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
2. Make the method `performSort(List<Colour>)` generic, so that other comparable objects can also be sorted by the same method. **Hint:** Have a look at Java Generics and the interface `Comparable`.
3. Extend the `Policy` to decide when to use the new `QuickSort` algorithm based on a different palette characteristic, such as the average hue.
