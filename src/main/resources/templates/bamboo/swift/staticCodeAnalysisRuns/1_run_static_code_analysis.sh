echo "---------- execute static code analysis ----------"
mkdir target
swiftlint lint assignment > target/swiftlint-result.xml
