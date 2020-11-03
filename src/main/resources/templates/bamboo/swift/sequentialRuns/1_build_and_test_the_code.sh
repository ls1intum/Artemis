# mv assignment to root
mv assignment/Sources .

echo "---------- swift build ----------"
swift build

echo "---------- swift test ----------"
swift test
