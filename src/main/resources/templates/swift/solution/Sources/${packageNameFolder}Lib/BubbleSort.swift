import Foundation

public class BubbleSort: SortStrategy {
    /**
     Sorts dates with BubbleSort.

     - Parameter input: the List of Dates to be sorted
    */
    public func performSort(_ input: inout [Date]) {
        for i in (0 ..< input.count).reversed() {
            for j in 0 ..< i where input[j] > input[j + 1] {
                    let temp: Date! = input[j]
                    input[j] = input[j + 1]
                    input[j + 1] = temp
            }
        }
    }
}
