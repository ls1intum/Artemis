echo "---------- execute static code analysis ----------"
mkdir target
swiftlint lint assignment > target/swiftlint-result.xml

#cd target
#echo " -- old checkstyle --"
#cat swiftlint-result.xml
## rename swiftlint.rules to swiftlint.blocks to fake the Java category
#sed -i 's/.rules./.blocks./g' swiftlint-result.xml
#echo " -- new checkstyle --"
#cat swiftlint-result.xml
