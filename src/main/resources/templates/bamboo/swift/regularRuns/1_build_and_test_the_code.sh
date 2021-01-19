# In order to get the correct console output we need to execute the command within the assignment directory
# save student's assignment to root
rm -rf Sources
mv assignment/Sources .
# delete and create the assignment dir from scratch
rm -rf assignment
mkdir assignment
cp -R Sources assignment
# copy test files
cp -R Tests assignment
cp Package.swift assignment

echo "---------- swift build ----------"
cd assignment
swift build || error=true

if [ ! $error ]
then
    echo "---------- swift test ----------"
    swift test || true
fi

# The used docker container is calling 'swift build' which creates files as root (e.g. tests.xml),
# so we need to allow bamboo to access these files
chmod -R 777 ${bamboo.working.directory}
