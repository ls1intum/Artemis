In the grand halls of the Arcane Library, ancient tomes have a habit of wandering back to the shelves on their own, each bearing a mysterious return date. The librarians have tasked you with taming this chaos by sorting the returned books efficiently, and by choosing the right sorting spell at runtime.

### Part 1: Sorting

First, you must bring to life two sorting spells, **BubbleSort** and **MergeSort**, that can order a list of **Book** objects.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Book>)` in the class `BubbleSort`. Follow the Bubble Sort incantation to the letter.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Book>)` in the class `MergeSort`. Follow the Merge Sort incantation to the letter.

### Part 2: Strategy Pattern

The library wishes to select the appropriate sorting spell based on how many books have returned at once. Use the Strategy Pattern to switch spells at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting spells so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the list holds more than 10 returned books.

    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the list holds ten or fewer returned books.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

class Client {
}

class Policy {
  <color:testsColor(testMethods[Policy])>+configure()</color>
}

class Context {
  <color:testsColor(testAttributes[Context])>-returnedBooks: List<Book></color>
  <color:testsColor(testMethods[Context])>+sort()</color>
}

interface SortStrategy {
  <color:testsColor(testMethods[SortStrategy])>+performSort(List<Book>)</color>
}

class BubbleSort {
  <color:testsColor(testBubbleSort)>+performSort(List<Book>)</color>
}

class MergeSort {
  <color:testsColor(testMergeSort)>+performSort(List<Book>)</color>
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

2. Make the method `performSort(List<Book>)` generic, so that other objects can also be sorted by the same method.
   **Hint:** Have a look at Java Generics and the interface `Comparable`.

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.