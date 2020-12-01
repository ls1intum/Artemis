echo "---------- execute static code analysis ----------"
mkdir target
swiftlint lint assignment > target/checkstyle-result.xml

cd target
echo " -- old checkstyle --"
cat checkstyle-result.xml

sed -i 's/.rules./.blocks./g' checkstyle-result.xml
echo " -- new checkstyle --"
cat checkstyle-result.xml
