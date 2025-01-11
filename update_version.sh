#!/bin/bash

# Script to update version in specified files and locations
# Usage: ./update_version.sh [major|minor|bugfix]

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 [major|minor|bugfix]"
    exit 1
fi

INCREMENT_TYPE=$1

# Validate the argument
if [[ "$INCREMENT_TYPE" != "major" && "$INCREMENT_TYPE" != "minor" && "$INCREMENT_TYPE" != "bugfix" ]]; then
    echo "Error: Invalid argument. Use one of [major, minor, bugfix]. No other values are allowed."
    exit 1
fi

# Function to increment version
increment_version() {
    local version=$1
    local type=$2

    IFS='.' read -r major minor bugfix <<< "$version"

    case $type in
        major)
            major=$((major + 1))
            minor=0
            bugfix=0
            ;;
        minor)
            minor=$((minor + 1))
            bugfix=0
            ;;
        bugfix)
            bugfix=$((bugfix + 1))
            ;;
    esac

    echo "$major.$minor.$bugfix"
}

# Read the current version from the first valid `version = "x.y.z"` in build.gradle
CURRENT_VERSION=$(awk -F'"' '/^version = "[0-9]+\.[0-9]+\.[0-9]+"/ {print $2; exit}' build.gradle)

if [[ -z "$CURRENT_VERSION" ]]; then
    echo "Could not find a valid version in build.gradle."
    exit 1
fi

# Get the new version
NEW_VERSION=$(increment_version "$CURRENT_VERSION" "$INCREMENT_TYPE")

# Update files
echo "Updating version from $CURRENT_VERSION to $NEW_VERSION..."

# Update build.gradle
sed -i '' "s/^version = \"$CURRENT_VERSION\"/version = \"$NEW_VERSION\"/" build.gradle

# Update package.json to only update the version when the previous line is `"name": "artemis"`
awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
    BEGIN {found_name = 0}
    /"name": "artemis"/ {found_name = 1}
    found_name && /"version": ".*"/ {
        sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
        found_name = 0
    }
    {print}
' package.json > package.json.tmp && mv package.json.tmp package.json

# Update package-lock.json to only update the version when the previous line is `"name": "artemis"`
awk -v old_version="$CURRENT_VERSION" -v new_version="$NEW_VERSION" '
    BEGIN {found_name = 0}
    /"name": "artemis"/ {found_name = 1}
    found_name && /"version": ".*"/ {
        sub("\"version\": \"" old_version "\"", "\"version\": \"" new_version "\"")
        found_name = 0
    }
    {print}
' package-lock.json > package-lock.json.tmp && mv package-lock.json.tmp package-lock.json

# Update README.md
sed -i '' "s/Artemis-$CURRENT_VERSION.war/Artemis-$NEW_VERSION.war/" README.md

# Add changes to git and commit
echo "Staging changes for git..."
git add build.gradle package.json README.md package-lock.json

echo "Creating git commit..."
git commit -m "Development: Bump version to $NEW_VERSION ($INCREMENT_TYPE update)"

echo "Version update and git commit complete. New version: $NEW_VERSION"
