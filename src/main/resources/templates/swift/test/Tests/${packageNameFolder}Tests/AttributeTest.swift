import Testing

@Suite struct AttributeTest {

    @Test func testAttributesContext() {
        checkAttributesFor("Context")
    }

    @Test func testAttributesPolicy() {
        checkAttributesFor("Policy")
    }

    /// Test implementation of attributes
    private func checkAttributesFor(_ className: String) {
        guard let structure = getSourceFileStructure(for: className)?.types[className] else {
            Issue.record("\(className).swift is not implemented!")
            return
        }

        guard let classFile = classFileOracle.first(where: { $0.name == className }) else {
            Issue.record("No tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide attributes information or delete testAttributes\(className)()!")
            return
        }

        /// check implementation of attributes
        guard let attributes = classFile.attributes else {
            Issue.record("No attribute tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide attributes information or delete testAttributes\(className)()!")
            return
        }

        for attribute in attributes where !structure.attributes.contains(attribute) {
            Issue.record("Attribute '\(attribute)' of \(classFile.name).swift is not implemented!")
        }
    }
}
