import Foundation
import Testing
@testable import ${packageName}Lib

/// A new instance of the suite is created for every test, so every test sorts its own random dates.
@Suite struct SortingExampleBehaviorTest {
    let orderedDates: [Date]
    let unorderedDates: [Date]

    init() {
        orderedDates = SortingExampleBehaviorTest.createOrderedDatesList(11)
        unorderedDates = orderedDates.shuffled()
    }

    private static func createOrderedDatesList(_ amount: Int) -> [Date] {
        var list = [Date]()
        var orderedSeconds: Double = 0.00
        for _ in 0 ..< amount {
            /// create random dates in an ascending order
            orderedSeconds += Double.random(in: 1000000...1000000000)
            list.append(Date(timeIntervalSince1970: orderedSeconds))
        }
        return list
    }

    @Test func testBubbleSort() {
        let bubbleSort = BubbleSort()
        let sortedInput = bubbleSort.performSort(unorderedDates)

        if sortedInput != orderedDates {
            Issue.record("BubbleSort does not sort correctly.")
        }
    }

    @Test func testMergeSort() {
        let mergeSort = MergeSort()
        let sortedInput = mergeSort.performSort(unorderedDates)

        if sortedInput != orderedDates {
            Issue.record("MergeSort does not sort correctly.")
        }
    }
}
