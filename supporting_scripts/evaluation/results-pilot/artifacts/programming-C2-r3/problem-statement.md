 1 | In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.
 2 | 
 3 | ### Part 1: Sorting
 4 | 
 5 | First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.
 6 | 
 7 | **You have the following tasks:**
 8 | 
 9 | 1. [task][Implement Bubble Sort](testBubbleSort)
10 | Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.
11 | 
12 | 2. [task][Implement Merge Sort](testMergeSort)
13 | Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.
14 | 
15 | **Additional Hard Tasks (edge cases):**
16 | 
17 | 3. [task][Empty List Bubble Sort](testEmptyListBubbleSort)
18 | Ensure that `BubbleSort.performSort` correctly handles an empty list (the list remains empty).
19 | 
20 | 4. [task][Empty List Merge Sort](testEmptyListMergeSort)
21 | Ensure that `MergeSort.performSort` correctly handles an empty list (the list remains empty).
22 | 
23 | 5. [task][Duplicate Dates Bubble Sort](testDuplicateDatesBubbleSort)
24 | Ensure that `BubbleSort.performSort` correctly sorts a list containing duplicate dates, preserving all elements.
25 | 
26 | 6. [task][Duplicate Dates Merge Sort](testDuplicateDatesMergeSort)
27 | Ensure that `MergeSort.performSort` correctly sorts a list containing duplicate dates, preserving all elements.
28 | 
 29 | 7. [task][Already Sorted List Bubble Sort](testAlreadySortedListBubbleSort)
30 | Ensure that `BubbleSort.performSort` leaves an already sorted list unchanged.
31 | 
32 | 8. [task][Already Sorted List Merge Sort](testAlreadySortedListMergeSort)
33 | Ensure that `MergeSort.performSort` leaves an already sorted list unchanged.
34 | 
 35 | ### Part 2: Strategy Pattern
 36 | 
 37 | We want the application to apply different algorithms for sorting a `List` of `Date` objects.
 38 | Use the strategy pattern to select the right sorting algorithm at runtime.
 39 | 
 40 | **You have the following tasks:**
 41 | 
 42 | 1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
 43 | Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.
 44 | 
 45 | 2. [task][Context Class](testClass[Context],testMethods[Context])
 46 | Create and implement a `Context` class following the below class diagram
 47 | 
 48 | 3. [task][Context Policy](testClass[Policy],testMethods[Policy])
 49 | Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:
 50 | 
 51 |     1. [task][Select MergeSort](testSelectMergeSort,testConfigureMergeSort)
 52 |     Select `MergeSort` when the List has more than 10 dates.
 53 | 
 54 |     2. [task][Select BubbleSort](testSelectBubbleSort,testConfigureBubbleSort)
 55 |     Select `BubbleSort` when the List has less or equal 10 dates.
 56 | 
 57 | 4. Complete the `Client` class which demonstrates switching between two strategies at runtime.
 58 | 
 59 | @startuml
 60 | 
 61 | class Client {
 62 | }
 63 | 
 64 | class Policy {
 65 |   <color:testsColor(testConfigureBubbleSort)>+configure()</color>
 66 | }
 67 | 
 68 | class Context {
 69 |   <color:testsColor(testClass[Context])>-dates: List<Date></color>
 70 |   <color:testsColor(testMethods[Context])>+sort()</color>
 71 | }
 72 | 
 73 | interface SortStrategy {
 74 |   <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>
 75 | }
 76 | 
 77 | class BubbleSort {
 78 |   <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
 79 | }
 80 | 
 81 | class MergeSort {
 82 |   <color:testsColor(testMergeSort)>+performSort(List<Date>)</color>
 83 | }
 84 | 
 85 | MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
 86 | BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
 87 | Policy -right-> Context #testsColor(testClass[Policy]): context
 88 | Context -right-> SortStrategy #testsColor(testClass[Context]): sortAlgorithm
 89 | Client .down.> Policy
 90 | Client .down.> Context
 91 | 
 92 | hide empty fields
 93 | hide empty methods
 94 | 
 95 | @enduml
 96 | 
 97 | ### Part 3: Optional Challenges
 98 | 
 99 | (These are not tested)
100 | 
101 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
102 | 
103 | 2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
104 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
105 | 
106 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.