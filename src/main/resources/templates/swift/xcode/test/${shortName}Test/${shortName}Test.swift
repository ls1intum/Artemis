//
//  ${shortName}Tests.swift
//  ${shortName}
//
//  Created by Daniel Kainz on 06.08.21.
//

import XCTest
@testable import ${shortName}

class ${shortName}Test: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    func testGreeting() {
        XCTAssertEqual(Greeting.greet(), "Hello, world!", "The greeting was not done correctly!")
    }
}
