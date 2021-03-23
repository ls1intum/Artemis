// swift-tools-version:5.2
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "${packageName}",
    dependencies: [
        // Dependencies declare other packages that this package depends on.
        .package(name: "SwiftTestReporter", url: "https://github.com/allegro/swift-junit.git", from: "2.0.0"),
        .package(url: "https://github.com/yanagiba/swift-ast.git", from: "0.19.9"),
    ],
    targets: [
        // Targets are the basic building blocks of a package. A target can define a module or a test suite.
        // Targets can depend on other targets in this package, and on products in packages which this package depends on.
        .target(name: "${packageName}Lib"),
        // We need the separate Lib directory in order for the tests to run correctly, because the main.swift would interfere.
        .target(name: "${packageName}App", dependencies: ["${packageName}Lib"]),
        .testTarget(
            name: "${packageName}Tests",
            dependencies: [
                "${packageName}Lib",
                "SwiftTestReporter",
                .product(name: "SwiftAST+Tooling", package: "swift-ast")
            ]),
    ]
)
