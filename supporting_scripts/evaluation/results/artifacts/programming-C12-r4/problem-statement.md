In the grand halls of the Celestial Library, ancient tomes and modern scrolls alike find their way back to the shelves after being borrowed by wandering scholars. Your quest is to devise a magical sorting system that orders the returned books by their return dates, and to weave a strategy that chooses the most efficient spell depending on how many books arrive at once.

### Part 1: Sorting

First, you must bring to life two sorting incantations, `MergeSort` and `BubbleSort`, that can order a list of **ReturnedBook** objects.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<ReturnedBook>)` in the class `BubbleSort`. Follow the Bubble Sort algorithm to the letter.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<ReturnedBook>)` in the class `MergeSort`. Follow the Merge Sort algorithm to the letter.

### Part 2: Strategy Pattern

The library wishes to apply different sorting spells for the stream of returned books. Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the list has more than 10 returned books.

    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the list has ten or fewer returned books.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testMethods[Policy])>+configure()</color>
}

class Context {
  <color:testsColor(testAttributes[Context])>-returnedBooks: List<ReturnedBook></color>
  <color:testsColor(testMethods[Context])>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethods[SortStrategy])>+performSort(List<ReturnedBook>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<ReturnedBook>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<ReturnedBook>)</color>
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

2. Make the method `performSort(List<ReturnedBook>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.