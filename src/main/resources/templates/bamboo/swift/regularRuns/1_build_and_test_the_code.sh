# Delete possible old Sources and replace with student's assignment Sources
rm -rf Sources
mv assignment/Sources .
# Delete and create the assignment directory from scratch
rm -rf assignment
mkdir assignment
cp -R Sources assignment
# copy test files
cp -R Tests assignment
cp Package.swift assignment

# In order to get the correct console output we need to execute the command within the assignment directory
# swift build
cd assignment
swift build || error=true

if [ ! $error ]
then
    # swift test
    swift test || true
fi

# The used docker container is calling 'swift build' which creates files as root (e.g. tests.xml),
# so we need to allow bamboo to access these files
chmod -R 777 ${bamboo.working.directory}
