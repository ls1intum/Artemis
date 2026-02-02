import Testing
import Foundation
@testable import ${packageName}Lib

@Suite("Sorting Behavior Tests")
struct SortingBehaviorTests {

    let orderedDates: [Date]
    let unorderedDates: [Date]

    init() {
        self.orderedDates = SortingBehaviorTests.createOrderedDatesList(11)
        self.unorderedDates = self.orderedDates.shuffled()
    }

    private static func createOrderedDatesList(_ amount: Int) -> [Date] {
        var list = [Date]()
        var orderedSeconds: Double = 0.00
        for _ in 0 ..< amount {
            // Create random dates in an ascending order
            orderedSeconds += Double.random(in: 1000000...1000000000)
            let orderedDate = Date(timeIntervalSince1970: orderedSeconds)
            list.append(orderedDate)
        }
        return list
    }

    @Test("BubbleSort sorts dates correctly")
    func bubbleSort() {
        let bubbleSort = BubbleSort()
        let sortedInput = bubbleSort.performSort(unorderedDates)

        #expect(sortedInput == orderedDates, "BubbleSort does not sort correctly.")
    }

    @Test("MergeSort sorts dates correctly")
    func mergeSort() {
        let mergeSort = MergeSort()
        let sortedInput = mergeSort.performSort(unorderedDates)

        #expect(sortedInput == orderedDates, "MergeSort does not sort correctly.")
    }
}
