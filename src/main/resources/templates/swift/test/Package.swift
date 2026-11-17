// swift-tools-version:6.0
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "${packageName}",
    platforms: [
        // SwiftSyntax requires at least macOS 10.15, which is only relevant when the tests run on a Mac.
        .macOS(.v13),
    ],
    dependencies: [
        // Dependencies declare other packages that this package depends on.
        // SwiftSyntax parses the source files of the students for the structural tests. Its major version is the Swift
        // version (602 is Swift 6.2), so this requirement allows the releases of Swift 6.2.
        .package(url: "https://github.com/swiftlang/swift-syntax.git", from: "602.0.0"),
    ],
    targets: [
        // Targets are the basic building blocks of a package. A target can define a module or a test suite.
        // Targets can depend on other targets in this package, and on products in packages which this package depends on.
        .target(name: "${packageName}Lib"),
        // We need the separate Lib directory in order for the tests to run correctly, because the main.swift would interfere.
        .executableTarget(name: "${packageName}App", dependencies: ["${packageName}Lib"]),
        .testTarget(
            name: "${packageName}Tests",
            dependencies: [
                "${packageName}Lib",
                .product(name: "SwiftParser", package: "swift-syntax"),
                .product(name: "SwiftSyntax", package: "swift-syntax"),
            ]),
    ]
)
