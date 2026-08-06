In this exercise, you are a master painter who needs to arrange his palette of colours in a harmonious order before each masterpiece. To achieve this, you will implement sorting algorithms and dynamically select the appropriate one based on the size of the colour collection.

### Part 1: Sorting

First, you need to implement two sorting algorithms, `MergeSort` and `BubbleSort`, that can order a list of `Colour` objects.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Colour>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Colour>)` in the class `MergeSort`. Follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

Your painting studio must decide which sorting technique to use at runtime, depending on how many colours are on the palette. Use the strategy pattern to switch algorithms dynamically.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the class diagram below.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a `Policy` class following the class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the palette contains more than 10 colours.

    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the palette contains 10 or fewer colours.

4. Complete the `Client` class which demonstrates switching between the two strategies at runtime, generating random colour palettes and printing them before and after sorting.

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

### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Colour>)` generic, so that other objects can also be sorted by the same method.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.