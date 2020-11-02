import XCTest
import SwiftTestReporter
import swiftExerciseTests

_ = TestObserver()

var tests = [XCTestCaseEntry]()
tests += swiftExerciseTests.__allTests()

XCTMain(tests)
