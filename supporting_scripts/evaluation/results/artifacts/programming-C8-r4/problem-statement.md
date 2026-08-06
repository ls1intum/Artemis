In the hidden towers of the Chronomancer Academy, apprentices are tasked with mastering the art of ordering the threads of time itself. Ancient scrolls speak of two legendary incantations – the gentle **Bubble Sort** chant and the swift **Merge Sort** spell – each capable of weaving a chaotic list of dates into a harmonious timeline.

### Part 1: Sorting

First, you must bring these incantations to life. The scrolls demand that you implement the two sorting algorithms, **BubbleSort** and **MergeSort**, to manipulate `List<Date>` objects.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Harness the bubbling currents of time by completing the method `performSort(List<Date>)` in the class `BubbleSort`. Follow the ancient Bubble Sort algorithm to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Invoke the merging tides of chronology by completing the method `performSort(List<Date>)` in the class `MergeSort`. Follow the classic Merge Sort algorithm precisely.

### Part 2: Strategy Pattern

The Academy's grand library requires a flexible conduit to select the proper spell at runtime, depending on the size of the temporal collection. You must employ the Strategy pattern to let the **Context** choose the right sorting incantation.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Conjure a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Forge a `Context` class following the class diagram below, capable of holding a list of dates and delegating sorting to the chosen strategy.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Craft a `Policy` class with a simple configuration mechanism, as depicted in the diagram.

   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      When the list contains more than 10 dates, the Policy should summon **MergeSort**.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      When the list contains 10 dates or fewer, the Policy should invoke **BubbleSort**.

4. Complete the `Client` class which demonstrates the shifting of strategies at runtime, allowing the apprentice to witness the sorting magic in action.

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

2. Make the method `performSort(List<Date>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.
