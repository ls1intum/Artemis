import XCTest
@testable import ${packageName}Lib

final class SortingExampleBehaviorTest: XCTestCase {
    var unorderedDates: [Date]!
    var orderedDates: [Date]!

    private func createOrderedDatesList(_ amount: Int) -> [Date] {
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

    /// This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        self.orderedDates = createOrderedDatesList(11)
        self.unorderedDates = self.orderedDates.shuffled()
    }

    func testBubbleSort() {
        let bubbleSort = BubbleSort()
        let sortedInput = bubbleSort.performSort(unorderedDates)

        XCTAssertEqual(sortedInput, orderedDates, "BubbleSort does not sort correctly.")
    }

    func testMergeSort() {
        let mergeSort = MergeSort()
        let sortedInput = mergeSort.performSort(unorderedDates)

        XCTAssertEqual(sortedInput, orderedDates, "MergeSort does not sort correctly.")
    }
}
