In the hidden archives of the Great Library of Aeons, the keepers must constantly arrange the scrolls of time. Each scroll bears a **Date** – a fragment of history that must be placed in perfect order before the temporal currents become chaotic.

The library has recently acquired two ancient sorting spells: **BubbleSort** and **MergeSort**. These spells can be invoked at runtime, but the archivist must decide which one to wield based on how many scrolls lie before them.

### Part 1: Sorting

First, you must bring the two sorting spells to life.

1. [task][Implement Bubble Sort](testBubbleSort)
   Imbue the class `BubbleSort` with the ability to **performSort(List<Date>)** using the classic Bubble Sort incantation.

2. [task][Implement Merge Sort](testMergeSort)
   Imbue the class `MergeSort` with the ability to **performSort(List<Date>)** using the legendary Merge Sort algorithm.

### Part 2: Strategy Pattern

The library’s magical framework requires a flexible way to choose the proper spell at runtime. Use the Strategy pattern to let the archivist switch between the two sorting spells depending on the size of the scroll collection.

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Conjure a `SortStrategy` interface and make both sorting spells implement it.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Create a `Context` class that holds the list of dates and a reference to the chosen `SortStrategy`.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Forge a `Policy` class that decides which spell to use:
   
   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      When the list contains **more than 10** dates, the policy should select `MergeSort`.
   
   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      When the list contains **10 or fewer** dates, the policy should select `BubbleSort`.

4. Complete the `Client` class so that it demonstrates the archivist switching between the two strategies as the scroll piles change size.

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

1. Conjure a new class `QuickSort` that implements `SortStrategy` and bring the Quick Sort spell to life.
2. Make the method `performSort(List<Date>)` generic, so that other magical objects can also be sorted.
3. Devise a clever rule in `Policy` for when to summon the new `QuickSort` spell.
