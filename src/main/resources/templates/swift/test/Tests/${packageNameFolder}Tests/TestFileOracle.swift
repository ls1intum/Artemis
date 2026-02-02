struct ClassFile: Sendable {
    let name: String
    let methods: [String]
    let attributes: [String]?

    init(name: String, methods: [String], attributes: [String]? = nil) {
        self.name = name
        self.methods = methods
        self.attributes = attributes
    }
}

/// Defines test structure
let classFileOracle = [
    ClassFile(
        name: "SortStrategy",
        methods: ["performSort"]),
    ClassFile(
        name: "Context",
        methods: ["setDates", "getDates", "setSortAlgorithm", "getSortAlgorithm", "sort"],
        attributes: ["sortAlgorithm", "dates"]),
    ClassFile(
        name: "Policy",
        methods: ["configure"],
        attributes: ["context"])
]
