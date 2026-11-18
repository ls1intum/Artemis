//
//  BubbleSort.swift
//  ${appName}
//

import Foundation

public class BubbleSort: SortStrategy {
    /**
     Sorts dates with BubbleSort.

     - Parameter input: the List of Dates to be sorted
    */
    
    /*
     Task 1.1:
     Implement the Bubble Sort algorithm
    */
    public func performSort(_ input: [Date]) -> [Date] {
        var sortedInput = input
        for i in (0 ..< sortedInput.count).reversed() {
            for j in 0 ..< i where sortedInput[j] > sortedInput[j + 1] {
                    let temp: Date! = sortedInput[j]
                    sortedInput[j] = sortedInput[j + 1]
                    sortedInput[j + 1] = temp
            }
        }
        return sortedInput
    }
}
