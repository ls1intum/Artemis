 1 | In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.
 2 | 
 3 | ### Part 1: Sorting
 4 | 
 5 | First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.
 6 | 
 7 | **You have the following tasks:**
 8 | 
 9 | 1. [task][Implement Bubble Sort](testBubbleSort())
10 | Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.
11 | 
12 | 2. [task][Implement Merge Sort](testMergeSort())
13 | Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.
14 | 
15 | 3. [task][Handle Null Input](testNullInputBubbleSort(),testNullInputMergeSort())
16 | Ensure that `performSort` throws an `IllegalArgumentException` when the supplied list is `null`.
17 | 
18 | 4. [task][Handle Empty List](testEmptyListBubbleSort(),testEmptyListMergeSort())
19 | Ensure that sorting an empty list leaves it unchanged and does not throw any exception.
20 | 
21 | 5. [task][Stability Check](testAlreadySortedList(),testDuplicateDates())
22 | Verify that sorting an already sorted list or a list containing duplicate dates results in a correctly sorted list without errors.
23 | 
24 | ### Part 2: Strategy Pattern
25 | 
26 | We want the application to apply different algorithms for sorting a `List` of `Date` objects.
27 | Use the strategy pattern to select the right sorting algorithm at runtime.
28 | 
29 | **You have the following tasks:**
30 | 
31 | 1. [task][SortStrategy Interface](testSortStrategyInterface(),testSortStrategyMethods())
32 | Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.
33 | 
34 | 2. [task][Context Class](testContextClass(),testContextMethods())
35 | Create and implement a `Context` class following the below class diagram
36 | 
37 | 3. [task][Context Policy](testPolicyClass(),testPolicyConfigure(),testPolicySelectMergeSort(),testPolicySelectBubbleSort())
38 | Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:
39 | 
40 |     1. [task][Select MergeSort](testSelectMergeSort(),testPolicySelectMergeSort())
41 |     Select `MergeSort` when the List has more than 10 dates.
42 | 
43 |     2. [task][Select BubbleSort](testSelectBubbleSort(),testPolicySelectBubbleSort())
44 |     Select `BubbleSort` when the List has less or equal 10 dates.
45 | 
46 | 4. Complete the `Client` class which demonstrates switching between two strategies at runtime.
47 | 
48 | @startuml
49 | 
50 | class Client {
51 | }
52 | 
53 | class Policy {
54 |   <color:testsColor(testPolicySelectBubbleSort())>+configure()</color>
55 | }
56 | 
57 | class Context {
58 |   <color:testsColor(testContextClass())>-dates: List<Date></color>
59 |   <color:testsColor(testContextMethods())>+sort()</color>
60 | }
61 | 
62 | interface SortStrategy {
63 |   <color:testsColor(testSortStrategyMethods())>+performSort(List<Date>)</color>
64 | }
65 | 
66 | class BubbleSort {
67 |   <color:testsColor(testBubbleSort())>+performSort(List<Date>)</color>
68 | }
69 | 
70 | class MergeSort {
71 |   <color:testsColor(testMergeSort())>+performSort(List<Date>)</color>
72 | }
73 | 
74 | MergeSort -up-|> SortStrategy #testsColor(testSelectMergeSort())
75 | BubbleSort -up-|> SortStrategy #testsColor(testSelectBubbleSort())
76 | Policy -right-> Context #testsColor(testPolicyClass()): context
77 | Context -right-> SortStrategy #testsColor(testContextMethods()): sortAlgorithm
78 | Client .down.> Policy
79 | Client .down.> Context
80 | 
81 | hide empty fields
82 | hide empty methods
83 | 
84 | @enduml
85 | 
86 | ### Part 3: Optional Challenges
87 | 
88 | (These are not tested)
89 | 
90 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
91 | 
92 | 2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
93 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
94 | 
95 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.
