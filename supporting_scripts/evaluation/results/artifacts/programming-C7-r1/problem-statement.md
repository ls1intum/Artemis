In the grand archives of the Chronomancers' Tower, apprentices are tasked with bringing order to the swirling vortex of time‑stamped scrolls. Each scroll bears a `Date` that marks a pivotal moment in history, and the tower's magical sorting mechanisms must be coaxed into arranging them correctly before the temporal storms arrive.

### Part 1: Sorting

First, the apprentices must master two ancient sorting incantations: **Bubble Sort** and **Merge Sort**. These spells, when correctly invoked, will rearrange the scrolls into chronological order.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Follow the Bubble Sort incantation to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Implement the method `performSort(List<Date>)` in the class `MergeSort`. Follow the Merge Sort incantation to the letter.

### Part 2: Strategy Pattern

The tower's grand scheduler must be able to choose the appropriate sorting spell at runtime, depending on how many scrolls need ordering. To achieve this, the apprentices will employ the **Strategy Pattern**, allowing the scheduler to switch between spells on the fly.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Create and implement a `Context` class following the class diagram below.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Create and implement a `Policy` class following the class diagram with a simple configuration mechanism:

   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      Select `MergeSort` when the list contains more than 10 dates.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      Select `BubbleSort` when the list contains 10 dates or fewer.

4. Complete the `Client` class which demonstrates switching between the two strategies at runtime.

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