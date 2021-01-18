import XCTest
@testable import ${packageName}Lib

class SortTests: XCTestCase {
    let dateFormat = DateFormatter()
    var unorderedDates: [Date]!
    var orderedDates: [Date]!

    static var allTests = [
        ("testBubbleSort", testBubbleSort),
        ("testMergeSort", testMergeSort),
    ]

    // This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        self.orderedDates = createOrderedDatesList(11)
        self.unorderedDates = self.orderedDates.shuffled()
    }

    func testBubbleSort() {
        let bubbleSort = BubbleSort()
        bubbleSort.performSort(&unorderedDates)

        XCTAssertEqual(unorderedDates!, orderedDates!, "BubbleSort does not sort correctly.")
    }

    func testMergeSort() {
        let mergeSort = MergeSort()
        mergeSort.performSort(&unorderedDates)

        XCTAssertEqual(unorderedDates!, orderedDates!, "MergeSort does not sort correctly.")
    }

    private func createOrderedDatesList(_ amount: Int) -> [Date] {
        var list = [Date]()
        let dateFormat = DateFormatter()
        dateFormat.dateFormat = "dd.MM.yyyy"
        dateFormat.timeZone = TimeZone(identifier: "UTC")
        var orderedSeconds: Double = 0.00
        for _ in 0 ..< amount {
            orderedSeconds += Double.random(in: 1000000...1000000000)
            let orderedDate: Date! = Date(timeIntervalSince1970: orderedSeconds)
            list.append(orderedDate)
        }
        return list
    }
}
