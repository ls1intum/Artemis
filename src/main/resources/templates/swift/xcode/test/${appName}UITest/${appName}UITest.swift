//
//  ${appName}TestUITests.swift
//  ${appName}TestUITests
//

import XCTest
@testable import ${appName}

class ${appName}UITest: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
        // UI tests must launch the application that they test.
        app = XCUIApplication(bundleIdentifier: "de.tum.in.ase")
        app.launch()
    }

    func testStartScreen() {
        XCTAssertTrue(app.staticTexts["Sorting"].exists, "The title of the screen is not correct!")
        XCTAssertFalse(app.staticTexts["Unsorted Dates"].exists, "The unsorted dates should not be displayed before creation!")
        XCTAssertFalse(app.staticTexts["Sorted Dates"].exists, "The sorted dates should not be displayed before creation and sorting!")
        XCTAssertTrue(app.buttons["Create Random Dates"].isEnabled, "The button for creating random dates should be enabled!")
        XCTAssertFalse(app.buttons["Sort"].isEnabled, "The button for sorting the dates should be disabled!")
    }

    func testCreateAndDisplayUnsortedDates() {
        XCTAssertTrue(app.buttons["Create Random Dates"].exists, "There is no button to create random dates!")
        app.buttons["Create Random Dates"].tap()
        XCTAssertTrue(app.staticTexts["Unsorted Dates"].exists, "The unsorted dates should be displayed after creation!")
    }
    
    func testReCreateAndDisplayUnsortedDates() {
        XCTAssertTrue(app.buttons["Create Random Dates"].exists, "There is no button to create random dates!")
        app.buttons["Create Random Dates"].tap()
        XCTAssertTrue(app.staticTexts["Unsorted Dates"].exists, "The unsorted dates should be displayed after creation!")
        XCTAssertTrue(app.buttons["Create Random Dates"].exists, "There is no button to recreate random dates!")
        app.buttons["Create Random Dates"].tap()
        XCTAssertTrue(app.staticTexts["Unsorted Dates"].exists, "The new unsorted dates should be displayed after creation!")
    }
    
    func testCreateSortAndDisplaySortedDates() {
        XCTAssertTrue(app.buttons["Create Random Dates"].exists, "There is no button to create random dates!")
        app.buttons["Create Random Dates"].tap()
        XCTAssertTrue(app.staticTexts["Unsorted Dates"].exists, "The unsorted dates should be displayed after creation!")
        XCTAssertTrue(app.buttons["Sort"].exists, "There is no button to sort the dates!")
        app.buttons["Sort"].tap()
        XCTAssertTrue(app.staticTexts["Sorted Dates"].exists, "The sorted dates should be displayed after sorting!")
    }
    
    func testSwitchSortingAlgorithm() {
        XCTAssertTrue(app.pickerWheels.element.exists, "There is no way to switch the sorting algorithm!")
        app.pickerWheels.element.swipeUp()
    }
}
