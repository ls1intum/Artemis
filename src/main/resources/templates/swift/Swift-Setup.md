# Client Code

# IntelliSense Swift

## Install plugin
`https://nshipster.com/vscode/`

## Find executable of Language Server Protocol
`xcrun -f sourcekit-lsp` 

## Execute it 
`xcrun sourcekit-lsp`

# Setup Package Manager
https://www.raywenderlich.com/1993018-an-introduction-to-swift-package-manager

## Create project 
To create an executable project, run the following command:
`swift package init --type=executable`

## Build the executable
`swift build`

## Execute program
`swift run [optional: targetName]`

# Build Configurations
The above commands use the debug configuration by default. There are more configurations available; the most common one is `release`.

The `debug` configuration compiles much faster than `release`, but compiling a binary with the release configuration results in increased optimization.

Add `--configuration` release to your command to change the configuration to release, and execute it:  
`swift run --configuration release`

# Integrating With Xcode
Now that your project is set up, you’re ready to generate an Xcode project.
Execute the following command in Terminal to do so:  
`swift package generate-xcodeproj`

This will download the dependencies and create an Xcode project — NAME.xcodeproj.S

# Unit Tests
Running the unit tests:
`swift test`

# Testing in XCode
`https://confluence.atlassian.com/bamboo/xcode-354353193.html`