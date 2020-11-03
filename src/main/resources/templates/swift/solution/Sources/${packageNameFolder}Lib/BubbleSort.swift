import Foundation

public class BubbleSort: SortStrategy {
	public func performSort(_ input: inout [Date]) {
        for i in (0 ..< input.count).reversed() {
            for j in 0 ..< i {
                if input[j] > input[j + 1] {
                    let temp: Date! = input[j]
                    input[j] = input[j+1]
                    input[j+1] = temp
                }
            }
        }
    }
}
