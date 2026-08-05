 1 | In this exercise, we want to implement a sorting algorithm and choose it based on runtime specific variables.
 2 | 
 3 | ### Part 1: Sorting
 4 | 
 5 | First, we need to implement a sorting algorithm, in this case `BubbleSort`.
 6 | 
 7 | **You have the following tasks:**
 8 | 
 9 | 1. [task][Implement Bubble Sort](testBubbleSort)
10 | Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.
11 | 
12 | ### Part 2: Strategy Pattern
13 | 
14 | We want the application to apply a sorting algorithm for a `List` of `Date` objects.
15 | Use the strategy pattern to select the sorting algorithm at runtime.
16 | 
17 | **You have the following tasks:**
18 | 
19 | 1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
20 | Create a `SortStrategy` interface and adjust the sorting algorithm so that it implements this interface.
21 | 
22 | 2. [task][Context Class](testAttributes[Context],testMethods[Context])
23 | Create and implement a `Context` class following the below class diagram
24 | 
25 | 3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
26 | Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:
27 | 
28 |     1. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)
29 |     Select `BubbleSort` regardless of the list size.
30 | 
31 | 4. Complete the `Client` class which demonstrates switching between the strategy at runtime.
32 | 
33 | @startuml
34 | 
35 | class Client {
36 | }
37 | 
38 | class Policy {
39 |   <color:testsColor(testMethods[Policy])>+configure()</color>
40 | }
41 | 
42 | class Context {
43 |   <color:testsColor(testAttributes[Context])>-dates: List<Date></color>
44 |   <color:testsColor(testMethods[Context])>+sort()</color>
45 | }
46 | 
47 | interface SortStrategy {
48 |   <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>
49 | }
50 | 
51 | class BubbleSort {
52 |   <color:testsColor(testBubbleSort)>+performSort(List<Date>)</color>
53 | }
54 | 
55 | Policy -right-> Context #testsColor(testAttributes[Policy]): context
56 | Context -right-> SortStrategy #testsColor(testAttributes[Context]): sortAlgorithm
57 | Client .down.> Policy
58 | Client .down.> Context
59 | 
60 | hide empty fields
61 | hide empty methods
62 | 
63 | @enduml
64 | 
65 | ### Part 3: Optional Challenges
66 | 
67 | (These are not tested)
68 | 
69 | 1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.
70 | 
71 | 2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.
72 | **Hint:** Have a look at Java Generics and the interface `Comparable`.
73 | 
74 | 3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.