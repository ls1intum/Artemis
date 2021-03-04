import XCTest
import Source

struct ClassFile {
    var name: String
    var methods: [String]
    var attributes: [String]?
}

public func classFromString(_ className: String) -> AnyClass! {
    /// get 'anyClass' with classname and namespace
    guard let cls: AnyClass = NSClassFromString("${packageName}Lib.\(className)") else {
        return nil
    }
    /// return AnyClass!
    return cls
}
