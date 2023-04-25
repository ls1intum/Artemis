export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8

# Create directory for the swiftlint output specified in the fastfile
mkdir target
# Execute static code analysis
fastlane sca
