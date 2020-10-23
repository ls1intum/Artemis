#!/bin/sh
# Arguments:
# $1: exercise short name as specified on Artemis
# $2: (optional) output folder name

# base URL to repositories
BASE="ssh://git@bitbucket.ase.in.tum.de:7999/$EXERCISE/$EXERCISE"

if [ -z "$1" ]; then
  echo "No exercise short name supplied."
  exit 1
fi

EXERCISE="$1"

if [ -z "$2" ]; then
  # use the exercise name if no output folder name is specified
  NAME="$1"
else
  NAME="$2"
fi

# clone the test repository
git clone "$BASE-tests.git" "$NAME" && \
  # clone the template repository
  git clone "$BASE-exercise.git" "$NAME/template" && \
  # copy the solution repository
  git clone "$BASE-solution.git" "$NAME/solution" && \
  # create an assignment folder from the template repository
  cp -R "$NAME/template" "$NAME/assignment" && \
  # remove the .git folder from the assignment folder
  rm -r "$NAME/assignment/.git/"
