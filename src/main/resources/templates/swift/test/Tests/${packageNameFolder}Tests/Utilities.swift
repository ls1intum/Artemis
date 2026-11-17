import Foundation
import SwiftParser
import SwiftSyntax

/// The members that one type declares directly.
struct TypeStructure {
    /// Names of the functions of the type
    var methods: Set<String> = []
    /// Names of the stored and computed properties of the type
    var attributes: Set<String> = []
}

/// The declarations of one source file of the students, collected with SwiftSyntax.
struct SourceFileStructure {
    /// The classes, structs, enums, protocols and actors by name. The members of an extension belong to the type that it extends.
    var types: [String: TypeStructure] = [:]
}

/// Walks the syntax tree of a source file and collects its types with their own members.
/// A function or property that is declared inside of a function, property, initializer, deinitializer or subscript is local and is not collected.
private final class StructureCollector: SyntaxVisitor {
    var structure = SourceFileStructure()

    /// The names of the types around the declaration that is visited, the innermost last. An extension of a type that has no plain name is nil.
    private var enclosingTypes: [String?] = []

    init() {
        super.init(viewMode: .sourceAccurate)
    }

    private func enter(_ name: String?) -> SyntaxVisitorContinueKind {
        if let name, structure.types[name] == nil {
            structure.types[name] = TypeStructure()
        }
        enclosingTypes.append(name)
        return .visitChildren
    }

    private func leave() {
        enclosingTypes.removeLast()
    }

    /// Records a member in the innermost type, which is none for a function or property outside of every type.
    private func record(_ update: (inout TypeStructure) -> Void) {
        guard let enclosing = enclosingTypes.last, let name = enclosing else {
            return
        }
        update(&structure.types[name, default: TypeStructure()])
    }

    override func visit(_ node: ClassDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.name.text)
    }

    override func visitPost(_ node: ClassDeclSyntax) {
        leave()
    }

    override func visit(_ node: StructDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.name.text)
    }

    override func visitPost(_ node: StructDeclSyntax) {
        leave()
    }

    override func visit(_ node: EnumDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.name.text)
    }

    override func visitPost(_ node: EnumDeclSyntax) {
        leave()
    }

    override func visit(_ node: ProtocolDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.name.text)
    }

    override func visitPost(_ node: ProtocolDeclSyntax) {
        leave()
    }

    override func visit(_ node: ActorDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.name.text)
    }

    override func visitPost(_ node: ActorDeclSyntax) {
        leave()
    }

    override func visit(_ node: ExtensionDeclSyntax) -> SyntaxVisitorContinueKind {
        enter(node.extendedType.as(IdentifierTypeSyntax.self)?.name.text)
    }

    override func visitPost(_ node: ExtensionDeclSyntax) {
        leave()
    }

    override func visit(_ node: FunctionDeclSyntax) -> SyntaxVisitorContinueKind {
        record { $0.methods.insert(node.name.text) }
        // what the body declares is local
        return .skipChildren
    }

    override func visit(_ node: VariableDeclSyntax) -> SyntaxVisitorContinueKind {
        for binding in node.bindings {
            if let identifier = binding.pattern.as(IdentifierPatternSyntax.self) {
                record { $0.attributes.insert(identifier.identifier.text) }
            }
        }
        // what an accessor or the initial value declares is local
        return .skipChildren
    }

    // The bodies of initializers, deinitializers and subscripts declare only local names, like the bodies of functions.
    override func visit(_ node: InitializerDeclSyntax) -> SyntaxVisitorContinueKind {
        .skipChildren
    }

    override func visit(_ node: DeinitializerDeclSyntax) -> SyntaxVisitorContinueKind {
        .skipChildren
    }

    override func visit(_ node: SubscriptDeclSyntax) -> SyntaxVisitorContinueKind {
        .skipChildren
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
