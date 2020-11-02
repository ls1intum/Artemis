import XCTest
@testable import swiftExerciseLib

class SortTests: XCTestCase {  
    let dateFormat: DateFormatter = DateFormatter()
    var unorderedDates: [Date]!
    var orderedDates: [Date]!
    
    static var allTests = [
        ("testBubbleSort", testBubbleSort),
        ("testMergeSort", testMergeSort),
    ]

    // This is the setUp() instance method.
    // It is called before each test method begins.
    override func setUp() {
        dateFormat.dateFormat = "dd.MM.yyyy"
        dateFormat.timeZone = TimeZone(identifier: "UTC")
        let date1 = dateFormat.date(from: "08.11.2016")!
        let date2 = dateFormat.date(from: "15.04.2017")!
        let date3 = dateFormat.date(from: "05.09.2018")!
        let date4 = dateFormat.date(from: "09.09.2018")!
        let date5 = dateFormat.date(from: "15.09.2018")!
        let date6 = dateFormat.date(from: "15.10.2018")!
        let date7 = dateFormat.date(from: "06.09.2019")!
        let date8 = dateFormat.date(from: "07.12.2019")!
        let date9 = dateFormat.date(from: "08.05.2020")!
        let date10 = dateFormat.date(from: "01.11.2020")!
        let date11 = dateFormat.date(from: "22.12.2020")!
        self.orderedDates = [date1, date2, date3, date4, date5, date6, date7, date8, date9, date10, date11]
        self.unorderedDates = [date2, date1, date3, date5, date4, date8, date10, date11, date7, date9, date6]
    }

    func testBubbleSort() {
        let bubbleSort: BubbleSort = BubbleSort()
        bubbleSort.performSort(&unorderedDates)

        XCTAssertEqual(unorderedDates, orderedDates, "BubbleSort does not sort correctly.")
    }
    
    func testMergeSort() {
        let mergeSort: MergeSort = MergeSort()
        mergeSort.performSort(&unorderedDates)

        XCTAssertEqual(unorderedDates, orderedDates, "MergeSort does not sort correctly.")
    }
}
