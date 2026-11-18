//
//  ${appName}Tests.swift
//  ${appName}
//

import XCTest
@testable import ${appName}

class ${appName}Test: XCTestCase {
    private var unsortedDates: [Date]!
    private var sortedDates: [Date]!

    override func setUp() {
        super.setUp()

        // Create a sorted array of random dates
        self.sortedDates = createOrderedDatesList(11)

        // Shuffle the above created dates to created an array of unsorted dates for testing
        self.unsortedDates = self.sortedDates.shuffled()
    }

    func testAttributesForContext() {
        let context = Context()
        let mirror = Mirror(reflecting: context)
        var dates = true
        var sortAlgorithm = true
        for child in mirror.children {
            if child.label == "dates" {
                dates = false
            }
            if child.label == "sortAlgorithm" {
                sortAlgorithm = false
            }
        }
        if dates {
            XCTFail("Attribute 'dates' of Context.swift is not implemented!")
        }
        if sortAlgorithm {
            XCTFail("Attribute 'sortAlgorithm' of Context.swift is not implemented!")
        }
    }

    func testAttributesForPolicy() {
        let policy = Policy(Context())
        let mirror = Mirror(reflecting: policy)
        var context = true
        for child in mirror.children {
            if child.label == "context" {
                context = false
            }
        }
        if context {
            XCTFail("Attribute 'context' of Policy.swift is not implemented!")
        }
    }

    func testBubbleSort() {
        let bubbleSort = BubbleSort()
        let sortedInput = bubbleSort.performSort(unsortedDates)

        XCTAssertEqual(sortedInput, sortedDates, "BubbleSort does not sort correctly.")
    }

    func testMergeSort() {
        let mergeSort = MergeSort()
        let sortedInput = mergeSort.performSort(unsortedDates)

        XCTAssertEqual(sortedInput, sortedDates, "MergeSort does not sort correctly.")
    }

    func testContextMergeSort() {
        let context = Context()
        context.setDates(unsortedDates)
        context.setSortAlgorithm(MergeSort())
        context.sort()

        XCTAssertEqual(context.getDates(), sortedDates, "MergeSort does not sort correctly.")
    }

    func testContextBubbleSort() {
        let context = Context()
        context.setDates(unsortedDates)
        context.setSortAlgorithm(BubbleSort())
        context.sort()

        XCTAssertEqual(context.getDates(), sortedDates, "BubbleSort does not sort correctly.")
    }

    func testPolicy() {
        let context = Context()
        let policy = Policy(context)
        policy.configure(sortAlgorithm: SortAlgorithm.MergeSort)

        XCTAssertEqual("${appName}.MergeSort", String(describing: context.getSortAlgorithm()), "The Policy does not configure the Sorting Algorithm correctly!")

        policy.configure(sortAlgorithm: SortAlgorithm.BubbleSort)

        XCTAssertEqual("${appName}.BubbleSort", String(describing: context.getSortAlgorithm()), "The Policy does not configure the Sorting Algorithm correctly!")
    }

    func testMainViewLogic() {
        let mainView = MainView()

        XCTAssertEqual(mainView.sortAlgorithm, SortAlgorithm.MergeSort, "The default sorting algorithm should be Merge Sort!")
        Policy(mainView.context).configure(sortAlgorithm: mainView.sortAlgorithm)

        XCTAssertEqual("${appName}.MergeSort", String(describing: mainView.context.getSortAlgorithm()), "The sorting algorithm was not set correctly!")
    }

    func testMainViewContextSort() {
        let mainView = MainView()
        mainView.context.setDates(unsortedDates)

        XCTAssertEqual(mainView.context.getDates(), unsortedDates, "The dates in the context were not set correctly!")

        mainView.context.setSortAlgorithm(MergeSort())
        mainView.context.sort()

        XCTAssertEqual(mainView.context.getDates(), sortedDates, "The dates in the context were not sorted correctly!")
    }

    private func createOrderedDatesList(_ amount: Int) -> [Date] {
        var list = [Date]()
        let dateFormat = DateFormatter()
        dateFormat.dateFormat = "dd.MM.yyyy"
        dateFormat.timeZone = TimeZone(identifier: "UTC")
        var orderedSeconds: Double = 0.00
        for _ in 0 ..< amount {
            /// create random dates in an ascending order
            orderedSeconds += Double.random(in: 1000000...1000000000)
            let orderedDate: Date! = Date(timeIntervalSince1970: orderedSeconds)
            list.append(orderedDate)
        }
        return list
    }
}
