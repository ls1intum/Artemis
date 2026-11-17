import Testing

@Suite struct ClassTest {

    @Test func testClassContext() {
        checkClassExists("Context")
    }

    @Test func testClassPolicy() {
        checkClassExists("Policy")
    }

    @Test func testClassSortStrategy() {
        checkClassExists("SortStrategy")
    }

    /// Test that the file of the class exists and declares a type with the name of the class
    private func checkClassExists(_ className: String) {
        guard getSourceFileStructure(for: className)?.types[className] != nil else {
            Issue.record("\(className).swift is not implemented!")
            return
        }
    }
}
