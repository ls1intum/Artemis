import XCTest

public func classFromString(_ className: String) -> AnyClass! {
    /// get 'anyClass' with classname and namespace
    guard let cls: AnyClass = NSClassFromString("${packageName}Lib.\(className)") else {
        print("Unexpected error.")
        return nil
    }
    /// return AnyClass!
    return cls
}

public func checkForClass(expectedClass: String, _ actual: Any, file: StaticString = #file, line: UInt = #line) {
    let actualMirror = Mirror(reflecting: actual)
    guard expectedClass == "\(actualMirror.subjectType)" else {
        XCTFail("Wrong class. Expected class was: \(expectedClass).swift. Received: \(actualMirror.subjectType).swift", file: file, line: line)
        return
    }
    return
}
