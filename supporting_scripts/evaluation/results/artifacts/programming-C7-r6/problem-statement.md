In the celestial archives of Artemis, the goddess of the hunt, the scrolls of time are filled with dates of upcoming festivals, celestial events, and heroic quests. To keep the heavens in order, Artemis entrusts you with the task of implementing sorting algorithms that can arrange these sacred dates on demand, and a clever strategy that chooses the right algorithm based on the size of the list.

### Part 1: Sorting

First, you must bring to life two classic sorting techniques, the swift **BubbleSort** and the elegant **MergeSort**, to bring order to the chaotic list of dates.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm to the letter, letting the dates bubble to their proper places.

2. [task][Implement Merge Sort](testMergeSort)
   Implement the method `performSort(List<Date>)` in the class `MergeSort`. Follow the Merge Sort algorithm faithfully, merging the constellations of dates into a harmonious sequence.

### Part 2: Strategy Pattern

Artemis wishes the heavens to decide which sorting spell to cast at runtime, depending on how many dates await sorting. Use the Strategy pattern to let the context choose the appropriate algorithm.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

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