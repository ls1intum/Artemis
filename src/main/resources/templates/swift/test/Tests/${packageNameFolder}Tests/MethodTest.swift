import XCTest

final class MethodTest: XCTestCase {

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
        guard let structure = getSourceFileStructure(for: className) else {
            XCTFail("\(className).swift is not implemented!")
            return
        }

        guard let classFile = classFileOracle.first(where: { $0.name == className }) else {
            XCTFail("No tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide methods information or delete testMethods\(className)()!")
            return
        }

        // Check implementation of methods
        for method in classFile.methods {
            if !structure.functions.contains(method) {
                XCTFail("Func '\(method)' of \(classFile.name).swift is not implemented!")
            }
        }
    }
}
