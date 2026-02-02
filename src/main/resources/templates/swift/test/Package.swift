// swift-tools-version:6.0
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "${packageName}",
    platforms: [
        .macOS(.v13)
    ],
    dependencies: [
        // SwiftSyntax for structural code analysis (replaces unmaintained swift-ast)
        // Version 600.x matches swift-tools-version:6.0 (versioning: 600=6.0, 601=6.1, 602=6.2)
        .package(url: "https://github.com/swiftlang/swift-syntax.git", from: "600.0.1"),
    ],
    targets: [
        // Targets are the basic building blocks of a package. A target can define a module or a test suite.
        // Targets can depend on other targets in this package, and on products in packages which this package depends on.
        .target(name: "${packageName}Lib"),
        // Executable target with main.swift - separate from Lib so tests can run correctly
        .executableTarget(name: "${packageName}App", dependencies: ["${packageName}Lib"]),
        .testTarget(
            name: "${packageName}Tests",
            dependencies: [
                "${packageName}Lib",
                .product(name: "SwiftSyntax", package: "swift-syntax"),
                .product(name: "SwiftParser", package: "swift-syntax"),
            ]),
    ]
)
