 1 | In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.
 2 | 
 3 | ### Part 1: Sorting
 4 | 
 5 | First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.
 6 | 
 7 | **You have the following tasks:**
 8 | 
 9 | 1. [task][Implement Bubble Sort](testBubbleSort)
10 | Implement the method `performSort(List<Colour>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.
11 | 
12 | 2. [task][Implement Merge Sort](testMergeSort)
13 | Implement the method `performSort(List<Colour>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.
14 | 
15 | ### Part 2: Strategy Pattern
 16 | 
 17 | We want the application to apply different algorithms for sorting a `List` of `Colour` objects.
 18 | Use the strategy pattern to select the right sorting algorithm at runtime.
 19 | 
 20 | **You have the following tasks:**
 21 | 
 22 | 1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
23 | Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.
24 | 
 25 | 2. [task][Context Class](testAttributes[Context],testMethods[Context])
26 | Create and implement a `Context` class following the below class diagram
27 | 
 28 | 3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
29 | Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:
30 | 
 31 |     1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)
32 |     Select `MergeSort` when the List has more than 10 colours.
33 | 
 34 |     2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
35 |     Select `BubbleSort` when the List has less or equal 10 colours.
36 | 
 37 | 4. Complete the `Client` class which demonstrates switching between two strategies at runtime.
 38 | 
 39 | @startuml
 40 | 
 41 | class Client {
 42 | }
 43 | 
 44 | class Policy {
 45 |   <color:testsColor(testMethods[Policy])>+configure()</color>
 46 | }
 47 | 
 48 | class Context {
 49 |   <color:testsColor(testAttributes[Context])>-colours: List<Colour></color>
 50 |   <color:testsColor(testMethods[Context])>+sort()</color>
 51 | }
 52 | 
 53 | interface SortStrategy {
 54 |   <color:testsColor(testMethods[SortStrategy])>+performSort(List<Colour>)</color>
 55 | }
 56 | 
 57 | class BubbleSort {
 58 |   <color:testsColor(testBubbleSort)>+performSort(List<Colour>)</color>
 59 | }
 60 | 
 61 | class MergeSort {
 62 |   <color:testsColor(testMergeSort)>+performSort(List<Colour>)</color>
 63 | }
 64 | 
 65 | MergeSort -up-|> SortStrategy #testsColor(testClass[MergeSort])
 66 | BubbleSort -up-|> SortStrategy #testsColor(testClass[BubbleSort])
 67 | Policy -right-> Context #testsColor(testAttributes[Policy]): context
 68 | Context -right-> SortStrategy #testsColor(testAttributes[Context]): sortAlgorithm
 69 | Client .down.> Policy
 70 | Client .down.> Context
 71 | 
 72 | hide empty fields
 73 | hide empty methods
 74 | 
 75 | @enduml
 76 | 
 77 | ### Part 3: Optional Challenges
 78 | 
 79 | (These are not tested)
 80 | 
 81 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
 82 | 
 83 | 2. Make the method `performSort(List<Colour>)` generic, so that other objects can also be sorted by the same method.
 84 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
 85 | 
 86 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.
