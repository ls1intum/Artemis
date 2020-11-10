import XCTest
import SwiftTestReporter
import ${packageName}Tests

_ = TestObserver()

var tests = [XCTestCaseEntry]()
tests += ${packageName}Tests.__allTests()

XCTMain(tests)
