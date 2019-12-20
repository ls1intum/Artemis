#!/usr/bin/env bash

cd src/main/resources/templates/c || exit 1

# Create folder structur for tests
mkdir -p github-actions/assignment
mkdir github-actions/tests
cp -r "$1"/* github-actions/assignment/
cp -r test/* github-actions/tests/
cp -r test/structural github-actions/
cd github-actions || exit 1
mv tests/Makefile.file tests/Makefile
mv assignment/Makefile.file assignment/Makefile


# Setup test environment
cd tests || exit 1
if [ -f "requirements.txt" ]; then
    pip3 install --user -r requirements.txt
else
    echo "requirements.txt does not exist"
fi
cd ..


# Setup MAKEFILE
shadowFilePath="../tests/testUtils/c/shadow_exec.c"

foundIncludeDirs=$(grep -m 1 'INCLUDEDIRS\s*=' assignment/Makefile)
echo "Include: $foundIncludeDirs"

foundSource=$(grep -m 1 'SOURCE\s*=' assignment/Makefile)
foundSource="$foundSource $shadowFilePath"
echo "Source: $foundSource"

rm -f assignment/GNUmakefile
rm -f assignment/makefile

cp -f tests/Makefile assignment/Makefile || exit 1
sed -i "s~INCLUDEDIRS\s*=.*~${foundIncludeDirs}~; s~SOURCE\s*=.*~${foundSource}~" assignment/Makefile


cd tests || exit 1
# Structural tests
cp structural/Tests.py ./
cp -r structural/tests ./tests
python3 Tests.py s
rm Tests.py
rm -rf ./tests

# Behavior tests
cp behavioral/Tests.py ./
cp -r behavioral/tests ./tests
python3 Tests.py b
