In the vaulted halls of the **Chronomancer's Library**, the keepers of time must arrange the ever‑shifting scrolls of dates so that the future can be read in order.  Your magical toolkit includes two ancient sorting spells – **BubbleSort** and **MergeSort** – and a clever **Strategy** rune that lets the library choose the right spell at runtime.

### Part 1: Sorting Spells

First, you must bring the two sorting spells to life.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Enchant the method `performSort(List<Date>)` in the class `BubbleSort` to follow the Bubble Sort incantation to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Enchant the method `performSort(List<Date>)` in the class `MergeSort` to follow the Merge Sort incantation to the letter.

### Part 2: Strategy Pattern of the Arcane

The library must decide which spell to invoke based on how many scrolls lie before it.  Use the **Strategy** pattern – a rune that can hold any sorting spell – to let the library switch spells at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Forge a `SortStrategy` interface and adjust the sorting spells so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Create and implement a `Context` class following the class diagram below.  It will hold the list of dates and the chosen spell.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Create and implement a `Policy` class following the diagram, with a simple configuration mechanism:

   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      Choose `MergeSort` when the list contains more than 10 dates.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      Choose `BubbleSort` when the list contains ten dates or fewer.

4. Complete the `Client` class which demonstrates the library switching between the two spells at runtime.

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