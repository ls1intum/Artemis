import XCTest

class ClassTest: XCTestCase {

    static var allTests = [
        ("testClassContext", testClassContext),
        ("testClassPolicy", testClassPolicy),
        ("testClassSortStrategy", testClassSortStrategy),
    ]

    /// This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testClassContext() {
        print("-> Testcase: testClassContext")
        let className = "Context"
        if classFromString(className) == nil {
            XCTFailClassNotFoundFor(className)
        }
    }

    func testClassPolicy() {
        print("-> Testcase: testClassPolicy")
        let className = "Policy"
        if classFromString(className) == nil {
            XCTFailClassNotFoundFor(className)
        }
    }

    func testClassSortStrategy() {
        print("-> Testcase: testClassSortStrategy")
        let className = "SortStrategy"
        if getClassSourceFileFor(className) == nil {
            XCTFailClassNotFoundFor(className)
        }
    }

    func XCTFailClassNotFoundFor(_ className: String) {
        XCTFail("\(className).swift is not implemented!")
    }
}
