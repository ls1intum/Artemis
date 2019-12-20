#!/usr/bin/env bash

cd src/main/resources/templates/java || exit 1

# Copy test template
mkdir -p github-actions/solution
cp -r test/projectTemplate/. github-actions/solution/

# Copy test files
mkdir github-actions/solution/test/githubactionssolution/testutils -p
cp -r test/testutils/. github-actions/solution/test/githubactionssolution/testutils
cp -r test/testFiles/. github-actions/solution/test/githubactionssolution

# Move behaviour & structural tests into one folder
mv github-actions/solution/test/githubactionssolution/structural/* github-actions/solution/test/githubactionssolution/
rm -r github-actions/solution/test/githubactionssolution/structural
mv github-actions/solution/test/githubactionssolution/behavior/* github-actions/solution/test/githubactionssolution/
rm -r github-actions/solution/test/githubactionssolution/behavior

# Copy assignment
mkdir github-actions/solution/assignment -p
cp -r solution/. github-actions/solution/assignment

# Remove everything that is sequential
sed -i '/%sequential-start%/,/%sequential-stop%/d' github-actions/solution/pom.xml

# Remove non-sequential section markers
sed -i '/%non-sequential-start%/d' github-actions/solution/pom.xml
sed -i '/%non-sequential-stop%/d' github-actions/solution/pom.xml

# Replace variables in files
find ./github-actions/solution -type f -exec sed -i 's/${packageName}/githubactionssolution/g' {} \;
find ./github-actions/solution -type f -exec sed -i 's/${packageNameFolder}/githubactionssolution/g' {} \;
find ./github-actions/solution -type f -exec sed -i 's/${exerciseNamePomXml}/githubactionssolution/g' {} \;
find ./github-actions/solution -type f -exec sed -i 's/${exerciseName}/githubactionssolution/g' {} \;

# Replace variables in directory
find ./github-actions/solution -name '${packageNameFolder}' -type d -exec bash -c 'mv "$1" "${1/\$\{packageNameFolder\}/githubactionssolution/}"' -- {} \;