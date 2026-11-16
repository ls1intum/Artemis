import Foundation
import SwiftParser
import SwiftSyntax

/// The declarations of one source file of the students, collected with SwiftSyntax.
struct SourceFileStructure {
    /// Names of the classes, structs, enums, protocols and actors
    var types: Set<String> = []
    /// Names of the functions, also the ones inside of types and protocols
    var methods: Set<String> = []
    /// Names of the stored and computed properties
    var attributes: Set<String> = []
}

/// Walks the syntax tree of a source file and collects its declarations.
private final class StructureCollector: SyntaxVisitor {
    var structure = SourceFileStructure()

    init() {
        super.init(viewMode: .sourceAccurate)
    }

    override func visit(_ node: ClassDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.types.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: StructDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.types.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: EnumDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.types.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: ProtocolDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.types.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: ActorDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.types.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: FunctionDeclSyntax) -> SyntaxVisitorContinueKind {
        structure.methods.insert(node.name.text)
        return .visitChildren
    }

    override func visit(_ node: VariableDeclSyntax) -> SyntaxVisitorContinueKind {
        for binding in node.bindings {
            if let identifier = binding.pattern.as(IdentifierPatternSyntax.self) {
                structure.attributes.insert(identifier.identifier.text)
            }
        }
        return .visitChildren
    }
}

/// Parses `Sources/${packageName}Lib/<fileName>.swift` of the repository under test.
/// Returns nil when the file does not exist or cannot be read.
func getSourceFileStructure(for fileName: String) -> SourceFileStructure? {
    let currentPath = FileManager.default.currentDirectoryPath
    let filePath = "\(currentPath)/Sources/${packageName}Lib/\(fileName).swift"
    guard let source = try? String(contentsOfFile: filePath, encoding: .utf8) else {
        return nil
    }
    let collector = StructureCollector()
    collector.walk(Parser.parse(source: source))
    return collector.structure
}
