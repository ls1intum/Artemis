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
15 | 3. [task][Handle null list in BubbleSort](testNullListBubbleSort)
16 | `performSort` must throw an `IllegalArgumentException` when the supplied list is `null`.
17 | 
18 | 4. [task][Handle null list in MergeSort](testNullListMergeSort)
19 | `performSort` must throw an `IllegalArgumentException` when the supplied list is `null`.
20 | 
21 | 5. [task][Handle empty list in BubbleSort](testEmptyListBubbleSort)
22 | Sorting an empty list must leave the list unchanged and not throw an exception.
23 | 
24 | 6. [task][Handle empty list in MergeSort](testEmptyListMergeSort)
25 | Sorting an empty list must leave the list unchanged and not throw an exception.
26 | 
27 | ### Part 2: Strategy Pattern
 28 | 
 29 | We want the application to apply different algorithms for sorting a `List` of `Date` objects.
 30 | Use the strategy pattern to select the right sorting algorithm at runtime.
 31 | 
 32 | **You have the following tasks:**
 33 | 
 34 | 1. [task][SortStrategy Interface](testSortStrategyInterface,testSortStrategyMethods)
 35 | Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.
 36 | 
 37 | 2. [task][Context Class](testContextClass,testContextMethods)
 38 | Create and implement a `Context` class following the below class diagram
 39 | 
 40 | 3. [task][Context Policy](testPolicyClass,testPolicyMethods,testPolicyConfigure)
 41 | Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:
 42 | 
 43 |     1. [task][Select MergeSort](testSelectMergeSort,testPolicyConfigureMerge)
 44 |     Select `MergeSort` when the List has more than 10 dates.
 45 | 
 46 |     2. [task][Select BubbleSort](testSelectBubbleSort,testPolicyConfigureBubble)
 47 |     Select `BubbleSort` when the List has less or equal 10 dates.
 48 | 
 49 | 4. [task][Context without strategy](testContextNoStrategy)
 50 | `Context.sort()` must throw an `IllegalStateException` if no `SortStrategy` has been configured.
 51 | 
 52 | 5. [task][Policy null dates handling](testPolicyNullDates)
 53 | `Policy.configure()` must throw an `IllegalArgumentException` if the context's date list is `null`.
 54 | 
 55 | 6. Complete the `Client` class which demonstrates switching between two strategies at runtime.
 56 | 
 57 | @startuml
 58 | 
 59 | class Client {
 60 | }
 61 | 
 62 | class Policy {
 63 |   <color:testsColor(testPolicyConfigure)>+configure()</color>
 64 | }
 65 | 
 66 | class Context {
 67 |   <color:testsColor(testContextClass)>-dates: List<Date></color>
 68 |   <color:testsColor(testContextMethods)>+sort()</color>
 69 | }
 70 | 
 71 | interface SortStrategy {
 72 |   <color:testsColor(testSortStrategyMethods)>+performSort(List<Date>)</color>
 73 | }
 74 | 
 75 | class BubbleSort {
 76 |   <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
 77 | }
 78 | 
 79 | class MergeSort {
 80 |   <color:testsColor(testMergeSort)>+performSort(List<Date>)</color>
 81 | }
 82 | 
 83 | MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort)
 84 | BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort)
 85 | Policy -right-> Context #testsColor(testPolicyClass): context
 86 | Context -right-> SortStrategy #testsColor(testContextClass): sortAlgorithm
 87 | Client .down.> Policy
 88 | Client .down.> Context
 89 | 
 90 | hide empty fields
 91 | hide empty methods
 92 | 
 93 | @enduml
 94 | 
 95 | ### Part 3: Optional Challenges
 96 | 
 97 | (These are not tested)
 98 | 
 99 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
100 | 
101 | 2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
102 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.