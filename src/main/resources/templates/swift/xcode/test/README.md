This is the combined repo that will be produced on the build agent by cloning two repos

1) exercise --> everything in the assignment folder
2) tests --> everything except the assignment folder

The tests can be executed as follows

`xcodebuild -derivedDataPath DerivedData -workspace XpenseTest.xcworkspace -scheme 'XpenseTest' -destination 'platform=iOS Simulator,name=iPhone 12 Pro Max,OS=14.5' test | /usr/local/bin/xcpretty --report junit --output "tests.xml"`

`fastlane test`


`xcodebuild -derivedDataPath DerivedData -workspace XpenseTest.xcworkspace -scheme 'XpenseUITest' -destination 'platform=iOS Simulator,name=iPhone 12 Pro Max,OS=14.5' test | /usr/local/bin/xcpretty --report junit --output "testsui.xml"`

`fastlane uitest`

`fastlane unittest`

