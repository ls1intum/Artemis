# copy assignment to root
cp -R assignment/Sources .

echo "---------- swift build ----------"
swift build

echo "---------- swift test ----------"
swift test || true

# The used docker container is calling 'swift build' which creates files as root (e.g. tests.xml),
# so we need to allow bamboo to access these files
chmod -R 777 ${bamboo.working.directory}
