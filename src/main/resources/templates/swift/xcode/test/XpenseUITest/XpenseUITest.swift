//
//  XpenseTestUITests.swift
//  XpenseTestUITests
//
//  Created by Daniel Kainz on 06.08.21.
//

import XCTest
@testable import Xpense

class XpenseUITest: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
        // UI tests must launch the application that they test.
        app = XCUIApplication(bundleIdentifier: "de.tum.in.ase.Xpense")
        app.launch()
    }

    func testGreetingUI() {
        XCTAssertTrue(app.staticTexts["Hello, world!"].exists, "The greeting was not presented correctly!")
    }
    
//    func testGreetingLogic() throws {
//        XCTAssertEqual(Xpense.Greeting.greet(), "Hello, world!", "The greeting was not done correctly!")
//    }
}
