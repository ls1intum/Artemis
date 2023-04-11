import XCTest
import AST
import Source
import Parser

public func classFromString(_ className: String) -> AnyClass! {
    /// Get 'anyClass' with classname and namespace
    guard let cls: AnyClass = NSClassFromString("${packageName}Lib.\(className)") else {
        return nil
    }
    /// return AnyClass!
    return cls
}

public func getClassSourceFileFor(_ className: String) -> SourceFile? {
    do {
        let currentPath = FileManager.default.currentDirectoryPath
        let filePath = "\(currentPath)/Sources/${packageName}Lib/\(className).swift"
        return try SourceReader.read(at: filePath)
    } catch {
        return nil
    }
}

public func getTopLevelDeclarationFor(_ className: String) -> TopLevelDeclaration? {
    do {
        if let sourceFile = getClassSourceFileFor(className) {
            let parser = Parser(source: sourceFile)
            return try parser.parse()
        }
        return nil
    } catch {
        return nil
    }
}
