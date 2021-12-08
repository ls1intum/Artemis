# Copy SwiftLint rules
cp .swiftlint.yml assignment || true
# create target directory for SCA Parser
mkdir target
cd assignment
# Execute static code analysis
swiftlint > ../target/swiftlint-result.xml
