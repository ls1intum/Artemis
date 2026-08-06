In the grand Library of Chronos, the keepers of time‑woven scrolls must ensure that the ancient dates etched upon them are always in perfect order. To achieve this, they enlist the aid of two mystical sorting spells – the swift BubbleSort incantation and the elegant MergeSort rite – and a clever strategy pattern that chooses the right spell based on the size of the scroll collection.

### Part 1: Sorting

First, the archivists must master the two sorting spells, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
   Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

The archive’s magical console must be able to invoke different sorting spells for a `List` of `Date` objects at runtime. Use the strategy pattern to select the right sorting spell based on the scroll collection’s size.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Create a `SortStrategy` interface and adjust the sorting spells so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      Select `MergeSort` when the List has more than 10 dates.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      Select `BubbleSort` when the List has less or equal 10 dates.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testMethods[Policy])>+configure()</color>
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

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Date>)</color>
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

2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.