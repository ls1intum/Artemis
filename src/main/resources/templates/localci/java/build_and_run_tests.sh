#!/bin/bash

# Create folder to clone the repositories into.
mkdir /repositories
cd /repositories

# Check out the test repository.
git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///test-repository

# Check out the assignment repository.
git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///assignment-repository

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
