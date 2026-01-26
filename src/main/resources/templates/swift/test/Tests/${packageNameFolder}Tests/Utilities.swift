import XCTest
import SwiftSyntax
import SwiftParser
import Foundation

// MARK: - Runtime Class Resolution

/// Attempts to load a class by name from the student's library module
public func classFromString(_ className: String) -> AnyClass? {
    return NSClassFromString("${packageName}Lib.\(className)")
}

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

// MARK: - AST Parsing

/// Parses a Swift source file and returns the syntax tree
/// - Parameter className: The name of the class to parse
/// - Returns: The parsed SourceFileSyntax, or nil if parsing fails
public func getParsedSyntax(for className: String) -> SourceFileSyntax? {
    guard let source = getSourceFileContents(for: className) else {
        return nil
    }
    return Parser.parse(source: source)
}

// MARK: - Structural Analysis

/// Collects structural information from a Swift source file
public struct SourceFileStructure {
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

/// Visitor that collects structural information from Swift syntax
private class StructureCollector: SyntaxVisitor {
    var classes: [String] = []
    var structs: [String] = []
    var protocols: [String] = []
    var enums: [String] = []
    var functions: [String] = []
    var properties: [String] = []

    init() {
        super.init(viewMode: .sourceAccurate)
    }

    override func visit(_ node: ClassDeclSyntax) -> SyntaxVisitorContinueKind {
        classes.append(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: StructDeclSyntax) -> SyntaxVisitorContinueKind {
        structs.append(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: ProtocolDeclSyntax) -> SyntaxVisitorContinueKind {
        protocols.append(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: EnumDeclSyntax) -> SyntaxVisitorContinueKind {
        enums.append(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: FunctionDeclSyntax) -> SyntaxVisitorContinueKind {
        functions.append(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: VariableDeclSyntax) -> SyntaxVisitorContinueKind {
        // Extract variable names from pattern bindings
        for binding in node.bindings {
            if let identifier = binding.pattern.as(IdentifierPatternSyntax.self) {
                properties.append(identifier.identifier.text)
            }
        }
        return .visitChildren
    }
}

/// Analyzes a source file and returns its structural information
/// - Parameter className: The name of the class file to analyze
/// - Returns: SourceFileStructure containing all declarations, or nil if parsing fails
public func getSourceFileStructure(for className: String) -> SourceFileStructure? {
    guard let syntax = getParsedSyntax(for: className) else {
        return nil
    }

    let collector = StructureCollector()
    collector.walk(syntax)

    return SourceFileStructure(
        classes: collector.classes,
        structs: collector.structs,
        protocols: collector.protocols,
        enums: collector.enums,
        functions: collector.functions,
        properties: collector.properties
    )
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
