# Delete possible old Sources and replace with student's assignment Sources
rm -rf ${packageName}
mv assignment/${packageName} .

#-destination 'platform=iOS Simulator,name=iPhone 12,OS=14.5'
#-sdk iphonesimulator14.5 (needs adjustments in Xcode)
xcodebuild -derivedDataPath DerivedData -scheme '${packageName}' -destination 'platform=iOS Simulator,name=iPhone 12,OS=14.5' test | /usr/local/bin/xcpretty --report junit --output "tests.xml"

echo "show all files"
ls -la
