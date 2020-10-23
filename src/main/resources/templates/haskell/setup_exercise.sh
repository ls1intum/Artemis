#!/bin/sh
# Arguments:
# $1: exercise short name as specified on Artemis
# $2: (optional) output folder name

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

BASE="ssh://git@bitbucket.ase.in.tum.de:7999/$EXERCISE/$EXERCISE"
git clone "$BASE-tests.git" "$NAME"
git clone "$BASE-exercise.git" "$NAME/template"
git clone "$BASE-solution.git" "$NAME/solution"
