import XCTest
import Runtime
import Foundation
import AST
import Parser
import Source

@testable import ${packageName}Lib

class StructuralTests: XCTestCase {

    static var allTests = [
        ("testContextProperties", testContextProperties),
        ("testPolicyProperties", testPolicyProperties),
        ("testSwiftAst", testSwiftAst),
    ]

    /// This is the setUp() instance method. It is called before each test method begins.
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testContextProperties() {
        print("-> Testcase: testContextProperties")
        if let inferredClass = classFromString("Context") {
            print("Class found:", inferredClass)

            do {
                let info = try typeInfo(of: inferredClass.self)
                /// sortAlgorithm
                do {
                    let _ = try info.property(named: "sortAlgorithm")
                } catch {
                    XCTFail("Property 'sortAlgorithm' of Context.swift is not implemented!")
                }
                /// dates
                do {
                    let _ = try info.property(named: "dates")
                } catch {
                    XCTFail("Property 'dates' of Context.swift is not implemented!")
                }
            } catch {
                XCTFail("Context.swift is not implemented!")
            }
        } else {
            XCTFail("Context.swift is not implemented!")
        }
    }

    func testPolicyProperties() {
        print("-> Testcase: testPolicyProperties")
        if let inferredClass = classFromString("Policy") {
            print("Class found:", inferredClass)

            do {
                let info = try typeInfo(of: inferredClass.self)
                print("----------------")
                // context
                do {
                    let _ = try info.property(named: "context")
                } catch {
                    XCTFail("Property 'context' of Policy.swift is not implemented!")
                }
            } catch {
                XCTFail("Policy.swift is not implemented!")
            }
        } else {
            XCTFail("Policy.swift is not implemented!")
        }
    }

    struct ClassFile {
        var name: String
        var methods: [String]
        var attributes: [String]?
    }

    func testSwiftAst() {
        print("-> Testcase: testSwiftAst")
        let classFiles = [
            ClassFile(name: "SortStrategy", methods: ["performSort"]),
            ClassFile(name: "Context", methods: ["setDates", "getDates", "setSortAlgorithm", "getSortAlgorithm", "sort"], attributes: ["sortAlgorithm", "dates"]),
            ClassFile(name: "Policy", methods: ["configure"], attributes: ["context"])]
        for classFile in classFiles {
            do {
                let currentPath = FileManager.default.currentDirectoryPath
                let filePath = "\(currentPath)/Sources/${packageName}Lib/\(classFile.name).swift"
                let sourceFile = try SourceReader.read(at: filePath)
                let parser = Parser(source: sourceFile)
                let topLevelDecl = try parser.parse()

                /// check implementation of attributes
                classFile.attributes?.forEach {
                    if !topLevelDecl.textDescription.contains("var \($0):") {
                        XCTFail("Attribute '\($0)' of \(classFile.name).swift is not implemented!")
                    }
                }

                /// check implementation of methods
                for method in classFile.methods where !topLevelDecl.textDescription.contains("func \(method)(") {
                    XCTFail("Func '\(method)' of \(classFile.name).swift is not implemented!")
                }
            } catch {
                XCTFail("\(classFile.name).swift is not implemented!")
            }
        }
    }
}
