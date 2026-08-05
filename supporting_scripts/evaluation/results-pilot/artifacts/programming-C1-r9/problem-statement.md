In this exercise, we want to implement a sorting algorithm and choose it based on runtime specific variables.

### Part 1: Sorting

First, we need to implement a sorting algorithm, in this case `BubbleSort`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a `SortStrategy` interface and adjust the sorting algorithm so that it implements this interface.

@startuml
class SortStrategy {
  +performSort(List<Date>)
}

class BubbleSort {
  +performSort(List<Date>)
}

BubbleSort -up-|> SortStrategy
hide empty fields
hide empty methods
@enduml