echo "---------- execute static code analysis ----------"
cp .swiftlint.yml assignment || true
mkdir target
swiftlint lint assignment > target/swiftlint-result.xml
