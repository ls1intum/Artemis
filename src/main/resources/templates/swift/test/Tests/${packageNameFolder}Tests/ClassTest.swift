import Testing

@Suite("Class Tests")
struct ClassTests {

    @Test("Context class exists")
    func classContext() throws {
        try #require(
            sourceFileExists(for: "Context"),
            "Context.swift is not implemented!"
        )
    }

    @Test("Policy class exists")
    func classPolicy() throws {
        try #require(
            sourceFileExists(for: "Policy"),
            "Policy.swift is not implemented!"
        )
    }

    @Test("SortStrategy protocol exists")
    func classSortStrategy() throws {
        try #require(
            sourceFileExists(for: "SortStrategy"),
            "SortStrategy.swift is not implemented!"
        )
    }
}
