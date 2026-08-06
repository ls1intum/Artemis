In the grand Hall of Chronomancers, ancient scrolls bearing dates flutter like restless spirits. The Archmage has tasked you, a fledgling sorcerer of algorithms, with mastering the art of ordering these temporal fragments. Two legendary incantations—**Bubble Sort** and **Merge Sort**—are said to tame the chaos, but they must be invoked correctly and at the right moment.

### Part 1: Sorting Incantations

First, you must breathe life into the two sorting spells.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Conjure the method `performSort(List<Date>)` inside the `BubbleSort` class. Follow the ancient Bubble Sort ritual to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Conjure the method `performSort(List<Date>)` inside the `MergeSort` class. Follow the ancient Merge Sort ritual to the letter.

### Part 2: Strategy Pattern – Choosing the Right Spell

The Archmage wishes the library to select the appropriate incantation based on the size of the scroll collection. Employ the Strategy Pattern so the correct spell is cast at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Forge a `SortStrategy` interface and bind the sorting incantations to it.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Create and implement a `Context` class following the diagram below. This class holds the scrolls and the chosen spell.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Craft a `Policy` class that decides which spell to wield:
   
   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      When the collection exceeds ten dates, summon `MergeSort`.
   
   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      When the collection holds ten or fewer dates, summon `BubbleSort`.

4. Complete the `Client` class so it demonstrates the shifting of spells as the library processes different batches of scrolls.

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

### Part 3: Optional Challenges (These are not tested)

1. Invent a new `QuickSort` spell that also implements `SortStrategy`.
2. Generalize `performSort(List<Date>)` to a generic method so that any comparable objects may be ordered.
3. Devise an additional decision rule in `Policy` for when to unleash the new `QuickSort` spell.

May your code be as swift as the wind and as orderly as the stars.