import Testing

@Suite struct MethodTest {

    @Test func testMethodsSortStrategy() {
        checkMethodsFor("SortStrategy")
    }

    @Test func testMethodsContext() {
        checkMethodsFor("Context")
    }

    @Test func testMethodsPolicy() {
        checkMethodsFor("Policy")
    }

    /// Test methods implementation
    private func checkMethodsFor(_ className: String) {
        guard let structure = getSourceFileStructure(for: className)?.types[className] else {
            Issue.record("\(className).swift is not implemented!")
            return
        }

        guard let classFile = classFileOracle.first(where: { $0.name == className }) else {
            Issue.record("No tests for class \(className) available in the structural oracle (TestFileOracle.swift). Either provide methods information or delete testMethods\(className)()!")
            return
        }

        /// check implementation of methods
        for method in classFile.methods where !structure.methods.contains(method) {
            Issue.record("Func '\(method)' of \(classFile.name).swift is not implemented!")
        }
    }
}
