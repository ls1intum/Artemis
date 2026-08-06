In the hidden halls of the Temporal Archive, ancient scrolls record the flow of time as **Date** objects. The archivists have long whispered of a mystical **Strategy Pattern** that can summon the right sorting spell depending on how many scrolls need ordering.

### Part 1: Enchanting the Sorts

Two legendary sorting incantations must be brought to life: the swift **MergeSort** and the diligent **BubbleSort**.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
   Invoke the ancient Bubble Sort rite by completing the method `performSort(List<Date>)` in the class `BubbleSort`. Follow the bubbling choreography to the letter.

2. [task][Implement Merge Sort](testMergeSort)
   Invoke the grand Merge Sort rite by completing the method `performSort(List<Date>)` in the class `MergeSort`. Follow the merging choreography to the letter.

### Part 2: The Strategy Conclave

The Archive must decide at runtime which spell to cast on a list of **Date** scrolls. The sages prescribe the **Strategy Pattern** to make this choice.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
   Forge a `SortStrategy` interface and bind the sorting incantations to it.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
   Conjure a `Context` class following the diagram below, the vessel that holds the scrolls and the chosen spell.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
   Craft a `Policy` class, the wise advisor that configures the `Context` with the proper spell:
   
   1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
      When the scroll collection exceeds ten dates, the advisor whispers **MergeSort**.

   2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
      When the collection holds ten or fewer dates, the advisor whispers **BubbleSort**.

4. Complete the `Client` class, the daring chronomancer who demonstrates the shifting of strategies at runtime.

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

1. Conjure a new class `QuickSort` that implements `SortStrategy` and weave the Quick Sort incantation.
2. Make the method `performSort(List<Date>)` generic, so that other enchanted objects may be sorted.
3. Ponder a clever decision rule in `Policy` for when to summon the new `QuickSort` spell.
