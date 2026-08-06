In the grand library of the ancient University of TUM, the diligent archivist Artemis is entrusted with the sacred scrolls of time—each scroll bearing a Date of a historic lecture. To keep the annals orderly, Artemis must wield powerful sorting spells and decide which spell to cast based on the size of the collection.

### Part 1: Sorting Spells

First, the archivist must master two ancient sorting incantations, known in the scrolls as **MergeSort** and **BubbleSort**.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Cast the Bubble Sort spell by implementing the method `performSort(List<Date>)` in the class `BubbleSort`. Follow the bubbling incantation to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Invoke the Merge Sort enchantment by implementing the method `performSort(List<Date>)` in the class `MergeSort`. Follow the merging rite precisely.

### Part 2: Strategy Pattern

The library’s magical framework must choose the appropriate sorting spell at runtime, depending on how many scrolls need ordering. The Strategy Pattern is the ancient mechanism that makes this possible.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Forge a `SortStrategy` interface and adjust the sorting spells so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Conjure a `Context` class following the diagram below, the conduit through which the spells are invoked.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Craft a `Policy` class that decides which spell to wield, based on a simple configuration:

   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      Choose `MergeSort` when the list holds more than 10 dates.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      Choose `BubbleSort` when the list holds ten dates or fewer.

4. Complete the `Client` class, the adventurous archivist, which demonstrates switching between the two strategies at runtime.

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