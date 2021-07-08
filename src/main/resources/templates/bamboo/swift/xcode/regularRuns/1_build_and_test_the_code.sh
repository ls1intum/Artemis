# Delete possible old Sources and replace with student's assignment Sources
rm -rf ${packageName}
mv assignment/${packageName} .

# Build project and write test results into xml file
xcodebuild -derivedDataPath DerivedData -scheme '${packageName}' -destination 'platform=iOS Simulator,name=iPhone 12,OS=14.5' test | /usr/local/bin/xcpretty --report junit --output "tests.xml"
