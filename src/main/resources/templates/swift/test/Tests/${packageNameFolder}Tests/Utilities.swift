import Foundation

// MARK: - Source File Reading

/// Reads the source file for a given class name
/// - Parameter className: The name of the class (matches filename without .swift)
/// - Returns: The source code as a string, or nil if the file doesn't exist
public func getSourceFileContents(for className: String) -> String? {
    let currentPath = FileManager.default.currentDirectoryPath
    let filePath = "\(currentPath)/Sources/${packageName}Lib/\(className).swift"
    return try? String(contentsOfFile: filePath, encoding: .utf8)
}

/// Checks if the source file exists for a given class name
/// - Parameter className: The name of the class
/// - Returns: true if the file exists and is readable
public func sourceFileExists(for className: String) -> Bool {
    return getSourceFileContents(for: className) != nil
}

// MARK: - Structural Analysis (Regex-based)

/// Collects structural information from a Swift source file
public struct SourceFileStructure: Sendable {
    public let classes: [String]
    public let structs: [String]
    public let protocols: [String]
    public let enums: [String]
    public let functions: [String]
    public let properties: [String]

    /// All type declarations (classes, structs, protocols, enums)
    public var allTypes: [String] {
        classes + structs + protocols + enums
    }
}

/// Analyzes a source file and returns its structural information using regex
/// - Parameter className: The name of the class file to analyze
/// - Returns: SourceFileStructure containing all declarations, or nil if file doesn't exist
public func getSourceFileStructure(for className: String) -> SourceFileStructure? {
    guard let source = getSourceFileContents(for: className) else {
        return nil
    }

    return SourceFileStructure(
        classes: findDeclarations(in: source, pattern: #"class\s+([A-Za-z_][A-Za-z0-9_]*)"#),
        structs: findDeclarations(in: source, pattern: #"struct\s+([A-Za-z_][A-Za-z0-9_]*)"#),
        protocols: findDeclarations(in: source, pattern: #"protocol\s+([A-Za-z_][A-Za-z0-9_]*)"#),
        enums: findDeclarations(in: source, pattern: #"enum\s+([A-Za-z_][A-Za-z0-9_]*)"#),
        functions: findDeclarations(in: source, pattern: #"func\s+([A-Za-z_][A-Za-z0-9_]*)"#),
        properties: findPropertyDeclarations(in: source)
    )
}

/// Finds all matches for a declaration pattern and returns the captured names
private func findDeclarations(in source: String, pattern: String) -> [String] {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
        return []
    }

    let range = NSRange(source.startIndex..., in: source)
    let matches = regex.matches(in: source, options: [], range: range)

    return matches.compactMap { match -> String? in
        guard match.numberOfRanges >= 2,
              let nameRange = Range(match.range(at: 1), in: source) else {
            return nil
        }
        return String(source[nameRange])
    }
}

/// Finds property declarations (var and let) in source code
private func findPropertyDeclarations(in source: String) -> [String] {
    // Match: var/let identifier followed by : or = (but not in function parameters)
    let pattern = #"(?:^|\n)\s*(?:public\s+|private\s+|internal\s+|fileprivate\s+)?(?:static\s+)?(?:var|let)\s+([A-Za-z_][A-Za-z0-9_]*)\s*[:\=]"#

    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.anchorsMatchLines]) else {
        return []
    }

    let range = NSRange(source.startIndex..., in: source)
    let matches = regex.matches(in: source, options: [], range: range)

    return matches.compactMap { match -> String? in
        guard match.numberOfRanges >= 2,
              let nameRange = Range(match.range(at: 1), in: source) else {
            return nil
        }
        return String(source[nameRange])
    }
}

// MARK: - Convenience Methods for Tests

/// Checks if a method exists in a source file
/// - Parameters:
///   - methodName: The name of the method to find
///   - className: The class file to search in
/// - Returns: true if the method is declared in the file
public func methodExists(_ methodName: String, in className: String) -> Bool {
    guard let structure = getSourceFileStructure(for: className) else {
        return false
    }
    return structure.functions.contains(methodName)
}

/// Checks if a property/attribute exists in a source file
/// - Parameters:
///   - propertyName: The name of the property to find
///   - className: The class file to search in
/// - Returns: true if the property is declared in the file
public func propertyExists(_ propertyName: String, in className: String) -> Bool {
    guard let structure = getSourceFileStructure(for: className) else {
        return false
    }
    return structure.properties.contains(propertyName)
}

/// Checks if a type (class, struct, protocol, or enum) exists in a source file
/// - Parameters:
///   - typeName: The name of the type to find
///   - className: The class file to search in
/// - Returns: true if the type is declared in the file
public func typeExists(_ typeName: String, in className: String) -> Bool {
    guard let structure = getSourceFileStructure(for: className) else {
        return false
    }
    return structure.allTypes.contains(typeName)
}
