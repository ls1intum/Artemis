import XCTest

class AttributeTest: XCTestCase {

    /// This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testAttributesContext() {
        print("-> Testcase: testMethodsContext")
        checkAttributesFor("Context")
    }

    func testAttributesPolicy() {
        print("-> Testcase: testMethodsPolicy")
        checkAttributesFor("Policy")
    }

    /// Test implementation of attributes
    func checkAttributesFor(_ className: String) {
        guard let structure = getSourceFileStructure(for: className) else {
            XCTFail("\(className).swift is not implemented!")
            return
        }

        guard let classFile = classFileOracle.first(where: { $0.name == className }) else {
            XCTFail("No tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide attributes information or delete testAttributes\(className)()!")
            return
        }

        guard let attributes = classFile.attributes else {
            XCTFail("No attribute tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide attributes information or delete testAttributes\(className)()!")
            return
        }

        // Check implementation of attributes
        for attribute in attributes {
            if !structure.properties.contains(attribute) {
                XCTFail("Attribute '\(attribute)' of \(classFile.name).swift is not implemented!")
            }
        }
    }
}
