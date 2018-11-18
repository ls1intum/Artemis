# ${exerciseName}

In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

**You have the following tasks:**

1. ✅[Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. ✅[Implement Merge Sort](testMergeSort)
Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a `List` of `Date` objects. 
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. ✅[SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.

2. ✅[Context Class](testClass[Context],testAttributes[Context],testMethods[Context])
Create and implement a `Context` class following the below class diagram

3. ✅[Context Policy](testClass[Policy],testConstructors[Policy],testAttributes[Policy],testMethods[Policy]) 
Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:

    1. ✅[Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
    Select `MergeSort` when the List has more than 10 dates.

    2. ✅[Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
    Select `BubbleSort` when the List has less or equal 10 dates.

4. Complete the `Client` class which demonstrates switching between two strategies at runtime.

@startuml

together {
    class Context
    class Policy
}
interface SortStrategy
class Client
class BubbleSort
class MergeSort

MergeSort -up-|> SortStrategy #testsColor(testFields[MergeSort])
BubbleSort -up-|> SortStrategy #testsColor(testFields[BubbleSort])

class Policy {
  +<color:testsColor(testMethods[Policy])>configure(): void</color>
}

class Context {
  -<color:testsColor(testAttributes[Context])>dates: List<Date></color>
  +<color:testsColor(testMethods[Context])>sort(): void</color>
}

interface SortStrategy {
  +<color:testsColor(testMethods[SortStrategy])>performSort(List<Date>): void</color>
}

class BubbleSort {
  +<color:testsColor(testMethods[BubbleSort])>performSort(List<Date>): void</color>
}

class MergeSort {
  +<color:testsColor(testMethods[MergeSort])>performSort(List<Date>): void</color>
}

Policy -left-> Context #testsColor(testFields[Policy]): context 
Context -right-> SortStrategy #testsColor(testFields[Context]): sortAlgorithm
Client ..> Policy
Client ..> Context 

@enduml


### Part 3: Optional Challenges

(These are not tested)

1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.

2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
**Hint:** Have a look at Java Generics and the interface `Comparable`.  

3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.
