import Foundation

public class MergeSort: SortStrategy {
    /**
     Sorts dates with MergeSort.

     - Parameter input: the List of Dates to be sorted
    */
    public func performSort(_ input: inout [Date]) {
        mergesort(&input, 0, input.count - 1)
    }

    /// Recursive merge sort method
    private func mergesort(_ input: inout [Date], _ low: Int, _ high: Int) {
        if (high - low) < 1 {
            return
        }
        let mid: Int = (low + high) / 2
        mergesort(&input, low, mid)
        mergesort(&input, mid + 1, high)
        merge(&input, low, mid, high)
    }

    /// Merge method
    private func merge(_ input: inout [Date], _ low: Int, _ middle: Int, _ high: Int) {
        var temp: [Date] = [Date]()
        var leftIndex: Int = low
        var rightIndex: Int = middle + 1
        while (leftIndex <= middle) && (rightIndex <= high) {
            if input[leftIndex] <= input[rightIndex] {
                temp.append(input[leftIndex])
                leftIndex += 1
            } else {
                temp.append(input[rightIndex])
                rightIndex += 1
            }
        }

        if (leftIndex <= middle) && (rightIndex > high) {
            while leftIndex <= middle {
                temp.append(input[leftIndex])
                leftIndex += 1
            }
        } else {
            while rightIndex <= high {
                temp.append(input[rightIndex])
                rightIndex += 1
            }
        }
        for wholeIndex in 0 ..< temp.count {
            input[wholeIndex + low] = temp[wholeIndex]
        }
    }
}
