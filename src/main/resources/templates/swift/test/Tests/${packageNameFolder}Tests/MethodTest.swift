import XCTest

class MethodTest: XCTestCase {

    static var allTests = [
        ("testMethodsSortStrategy", testMethodsSortStrategy),
        ("testMethodsContext", testMethodsContext),
        ("testMethodsPolicy", testMethodsPolicy),
    ]

    /// This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testMethodsSortStrategy() {
        print("-> Testcase: testMethodsSortStrategy")
        checkMethodsFor("SortStrategy")
    }

    func testMethodsContext() {
        print("-> Testcase: testMethodsContext")
        checkMethodsFor("Context")
    }

    func testMethodsPolicy() {
        print("-> Testcase: testMethodsPolicy")
        checkMethodsFor("Policy")
    }

    /// Test methods implementation
    func checkMethodsFor(_ className: String) {
        guard let topLevelDecl = getTopLevelDeclarationFor(className) else {
            XCTFail("\(className).swift is not implemented!")
            return
        }

        guard let classFile = classFileOracle.first(where: { $0.name == className }) else {
            XCTFail("No tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide attributes information or delete testMethods\(className)()!")
            return
        }

        /// check implementation of methods
        for method in classFile.methods where !topLevelDecl.textDescription.contains("func \(method)(") {
            XCTFail("Func '\(method)' of \(classFile.name).swift is not implemented!")
        }
    }
}
