 1 | In this exercise, we want to implement a sorting algorithm and choose it at runtime using the strategy pattern.
 2 |
 3 | ### Part 1: Sorting
 4 |
 5 | First, we need to implement the sorting algorithm `BubbleSort`.
 6 |
 7 | **You have the following tasks:**
 8 |
 9 | 1. [task][Implement Bubble Sort](testBubbleSort)
10 | Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.
11 |
12 | ### Part 2: Strategy Pattern
13 |
14 | We want the application to apply the sorting algorithm for a `List` of `Date` objects.
15 | Use the strategy pattern to select the algorithm at runtime.
16 |
17 | **You have the following tasks:**
18 |
19 | 1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
20 | Create a `SortStrategy` interface.
21 |
22 | 2. [task][Context Class](testClass[Context],testMethods[Context])
23 | Create and implement a `Context` class following the class diagram below.
24 |
25 | 3. [task][Policy Class](testClass[Policy])
26 | Create a `Policy` class that holds a reference to a `Context` and provides a `configure()` method. The method may simply set any `SortStrategy` on the context (the specific choice is not tested).
27 |
28 | 4. Complete the `Client` class which demonstrates switching between the strategy at runtime.
29 |
30 | @startuml
31 |
32 | class Client {
33 | }
34 |
35 | class Policy {
36 |   +configure()
37 | }
38 |
39 | class Context {
40 |   -dates: List<Date>
41 |   +sort()
42 | }
43 |
44 | interface SortStrategy {
45 |   +performSort(List<Date>)
46 | }
47 |
48 | class BubbleSort {
49 |   +performSort(List<Date>)
50 | }
51 |
52 | class MergeSort {
53 |   +performSort(List<Date>)
54 | }
55 |
56 | MergeSort -up-|> SortStrategy
57 | BubbleSort -up-|> SortStrategy
58 | Policy -right-> Context : context
59 | Context -right-> SortStrategy : sortAlgorithm
60 | Client .down.> Policy
61 | Client .down.> Context
62 |
63 | hide empty fields
64 | hide empty methods
65 |
66 | @enduml
67 |
68 | ### Part 3: Optional Challenges
69 |
70 | (These are not tested)
71 |
72 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
73 |
74 | 2. Make the method `performSort(List<Date>)` generic, so that other objects can also be sorted by the same method.
75 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
76 |
77 | 3. Extend `Policy` to choose between `BubbleSort` and `MergeSort` based on the list size.
