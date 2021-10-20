//
//  MergeSort.swift
//  ${appName}
//

import Foundation

public class MergeSort: SortStrategy {
    /**
     Sorts dates with MergeSort.

     - Parameter input: the List of Dates to be sorted
    */
    
    /*
     Task 1.2:
     Implement the Merge Sort algorithm
    */
    public func performSort(_ input: [Date]) -> [Date] {
        return mergesort(input, 0, input.count - 1)
    }

    /// Recursive merge sort method
    private func mergesort(_ input: [Date], _ low: Int, _ high: Int) -> [Date] {
        var sortedInput = input
        if (high - low) < 1 {
            /// break recursion and return last input
            return input
        }
        let mid: Int = (low + high) / 2
        sortedInput = mergesort(sortedInput, low, mid)
        sortedInput = mergesort(sortedInput, mid + 1, high)
        sortedInput = merge(sortedInput, low, mid, high)
        return sortedInput
    }

    /// Merge method
    private func merge(_ input: [Date], _ low: Int, _ middle: Int, _ high: Int) -> [Date] {
        var sortedInput = input
        var temp: [Date] = [Date]()
        var leftIndex: Int = low
        var rightIndex: Int = middle + 1
        while (leftIndex <= middle) && (rightIndex <= high) {
            if sortedInput[leftIndex] <= sortedInput[rightIndex] {
                temp.append(sortedInput[leftIndex])
                leftIndex += 1
            } else {
                temp.append(sortedInput[rightIndex])
                rightIndex += 1
            }
        }

        if (leftIndex <= middle) && (rightIndex > high) {
            while leftIndex <= middle {
                temp.append(sortedInput[leftIndex])
                leftIndex += 1
            }
        } else {
            while rightIndex <= high {
                temp.append(sortedInput[rightIndex])
                rightIndex += 1
            }
        }
        for wholeIndex in 0 ..< temp.count {
            sortedInput[wholeIndex + low] = temp[wholeIndex]
        }
        return sortedInput
    }
}
