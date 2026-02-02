import Testing

@Suite("Method Tests")
struct MethodTests {

    @Test("SortStrategy has required methods")
    func methodsSortStrategy() throws {
        try checkMethodsFor("SortStrategy")
    }

    @Test("Context class has required methods")
    func methodsContext() throws {
        try checkMethodsFor("Context")
    }

    @Test("Policy class has required methods")
    func methodsPolicy() throws {
        try checkMethodsFor("Policy")
    }

    /// Verify that all required methods are implemented in the given class
    private func checkMethodsFor(_ className: String) throws {
        let structure = try #require(
            getSourceFileStructure(for: className),
            "\(className).swift is not implemented!"
        )

        let classFile = try #require(
            classFileOracle.first(where: { $0.name == className }),
            "No tests for class \(className) available in the structural oracle (TestFileOracle.swift)."
        )

        // Check implementation of methods
        for method in classFile.methods {
            #expect(
                structure.functions.contains(method),
                "Func '\(method)' of \(classFile.name).swift is not implemented!"
            )
        }
    }
}
