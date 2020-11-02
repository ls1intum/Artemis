# As swift build creates files as root, we need to allow bamboo
# to access these files
chmod -R 777 ${bamboo.working.directory}
