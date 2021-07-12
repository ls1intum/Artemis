import XCTest
@testable import ${packageName}

class ${packageName}Tests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
        try super.setUpWithError()
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
        try super.tearDownWithError()
    }

    func testSampleText() throws {
        // This is an example of a functional test case.
        // Use XCTAssert and related functions to verify your tests produce the correct results.
        let sample = SampleText()
        sample.text = "My Artemis Test"
        XCTAssertEqual(sample.getText(), "My Artemis Test", "The sampleText gets not set correctly")
    }
}
