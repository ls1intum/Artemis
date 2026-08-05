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
15 | ### Part 2: Strategy Pattern
16 | 
17 | We want the application to apply different algorithms for sorting a `List` of `Date` objects.
18 | Use the strategy pattern to select the right sorting algorithm at runtime.
19 | 
20 | **You have the following tasks:**
21 | 
22 | 1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
23 | Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.
24 | 
25 | 2. [task][Context Class](testClass[Context],testMethods[Context])
26 | Create and implement a `Context` class following the below class diagram.
27 | 
28 | @startuml
29 | 
30 | class Client {
31 | }
32 | 
33 | class Context {
34 |   <color:testsColor(testClass[Context])>+dates: List<Date></color>
35 |   <color:testsColor(testMethods[Context])>+sort()</color>
36 | }
37 | 
38 | interface SortStrategy {
39 |   <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>
40 | }
41 | 
42 | class BubbleSort {
43 |   <color:testsColor(testBubbleSort())>+performSort(List<Date>)</color>
44 | }
45 | 
46 | class MergeSort {
47 |   <color:testsColor(testMergeSort())>+performSort(List<Date>)</color>
48 | }
49 | 
50 | MergeSort -up-|> SortStrategy #testsColor(testMergeSort())
51 | BubbleSort -up-|> SortStrategy #testsColor(testBubbleSort())
52 | Context -right-> SortStrategy #testsColor(testClass[Context]): sortAlgorithm
53 | Client .down.> Context
54 | 
55 | hide empty fields
56 | hide empty methods
57 | 
58 | @enduml
59 | 
60 | ### Part 3: Optional Challenges
61 | 
62 | (These are not tested)
63 | 
64 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
65 | 
66 | 2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
67 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
68 | 
69 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.
