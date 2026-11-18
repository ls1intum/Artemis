struct ClassFile {
    var name: String
    var methods: [String]
    var attributes: [String]?
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
