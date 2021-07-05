import XCTest

class ${packageName}UITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testUI() throws {
        XCTAssertTrue(app.staticTexts["Hello, Artemis!"].exists, "The UI has the wrong text message.")
    }
}
