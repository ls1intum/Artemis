In this exercise, you are a master painter who needs to organize a vibrant colour palette before each masterpiece. The palette consists of many colour swatches, each with a specific hue. To create harmonious compositions, you must sort these colours efficiently and switch between sorting strategies depending on the size of the palette.

### Part 1: Sorting

First, implement two sorting algorithms that can order a `List<Colour>`: `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Colour>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Colour>)` in the class `MergeSort`. Follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

Your painting application should choose the appropriate sorting algorithm at runtime based on how many colour swatches are in the palette. Use the strategy pattern to select the right algorithm.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the palette has more than 10 colours.

    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the palette has 10 or fewer colours.

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

1. Ensure that the sorting algorithms are **stable**: colours with identical hue values must retain their original order after sorting.
2. Create a new class `Colour` that represents a colour swatch. It should contain a `String name` and an `int rgb` value, implement `Comparable<Colour>` based on the `rgb` value, and correctly override `equals` and `hashCode`.
3. Make the `Policy` robust against empty or null palettes, handling them gracefully.

These challenges are part of the hard difficulty and will be tested.
