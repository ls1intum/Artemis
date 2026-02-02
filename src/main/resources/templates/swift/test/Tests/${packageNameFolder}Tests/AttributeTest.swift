import Testing

@Suite("Attribute Tests")
struct AttributeTests {

    @Test("Context class has required attributes")
    func attributesContext() throws {
        try checkAttributesFor("Context")
    }

    @Test("Policy class has required attributes")
    func attributesPolicy() throws {
        try checkAttributesFor("Policy")
    }

    /// Verify that all required attributes are implemented in the given class
    private func checkAttributesFor(_ className: String) throws {
        let structure = try #require(
            getSourceFileStructure(for: className),
            "\(className).swift is not implemented!"
        )

        let classFile = try #require(
            classFileOracle.first(where: { $0.name == className }),
            "No tests for class \(className) available in the structural oracle (TestFileOracle.swift)."
        )

        let attributes = try #require(
            classFile.attributes,
            "No attribute tests for class \(className) available in the structural oracle (TestFileOracle.swift)."
        )

        // Check implementation of attributes
        for attribute in attributes {
            #expect(
                structure.properties.contains(attribute),
                "Attribute '\(attribute)' of \(classFile.name).swift is not implemented!"
            )
        }
    }
}
