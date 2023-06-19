#!/bin/bash

# Create folder to clone the repositories into.
mkdir /repositories
cd /repositories

# Clone and checkout the test repository.
git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///test-repository

# Clone the assignment repository.
git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///assignment-repository
# Fetch and checkout the commit defined in an environment variable, if it is available.
cd assignment-repository

# Do another fetch for the commit hash as it might be older and not part of the shallow clone.
# TODO: Optimize this by only running this additional fetch if the $ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH is not part of the shallow clone.
if [ -n "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH" ]; then
    git fetch --depth 1 origin "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH"
    git checkout "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH"
fi

# Create the folder "assignment" in the test-repository.
mkdir /repositories/test-repository/assignment

# Copy the content of the assignment-repository into the folder "assignment" in the test-repository.
cp -a /repositories/assignment-repository/. /repositories/test-repository/assignment/

# Execute the tests.
cd /repositories/test-repository

if [ "$ARTEMIS_BUILD_TOOL" = "gradle" ]
then
  chmod +x gradlew
  # Remove carriage returns from gradlew in case the file was copied from a Windows machine.
  sed -i -e 's/\r$//' gradlew
  ./gradlew clean test
else
  mvn clean test
fi
