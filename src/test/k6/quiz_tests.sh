#!/usr/bin/env bash

currentDir=$(pwd)
baseDir=$currentDir/src/test/k6

# param parsing
PARAMS=""
while (( "$#" )); do
  case "$1" in
    -bu|--baseUrl) # URL of the sut
      baseUrl=$2
      shift 2
      ;;
    -i|--iterations) # How many students should try to participate?
      iterations=$2
      shift 2
      ;;
    -tp|--timeoutParticipation) # Timeout for participations in seconds
      timeoutParticipation=$2
      shift 2
      ;;
    -p|--password) # Base password for all test users
      basePassword=$2
      shift 2
      ;;
    -u|--username) # Base username for all test users
      baseUsername=$2
      shift 2
      ;;
    -c|--course-id) # Course id of the quiz
      courseId=$2
      shift 2
      ;;
    -e|--exercise-id) # Exercise id of the quiz
      exerciseId=$2
      shift 2
      ;;
    -o|--user-offset) # Offset for the user id
      userOffset=$2
      shift 2
      ;;
    --tests)
      tests=$2
      shift 2
      ;;
    --) # end argument parsing
      shift
      break
      ;;
    -*) # unsupported flags
      echo "Error: Unsupported flag $1" >&2
      exit 1
      ;;
    *) # preserve positional arguments
      PARAMS="$PARAMS $1"
      shift
      ;;
  esac
done
# set positional arguments in their proper place
eval set -- "$PARAMS"

# Exceptions and defaults
baseUrl=${baseUrl:?You have to specify the base URL}
basePassword=${basePassword:?"You have to specify the test user's base password"}
baseUsername=${baseUsername:?"You have to specify the test user's base username"}
iterations=${iterations:-10}
timeoutParticipation=${timeoutParticipation:-60}
userOffset=${userOffset:-0}

echo "################### STARTING Quiz Tests ###################"
result=$(docker run -i --rm --network=host --name quiz-test-"$userOffset" -v "$baseDir":/src -e BASE_USERNAME="$baseUsername" -e BASE_URL="$baseUrl" \
  -e BASE_PASSWORD="$basePassword" -e ITERATIONS="$iterations" -e TIMEOUT_PARTICIPATION="$timeoutParticipation" -e COURSE_ID="$courseId" \
  -e EXERCISE_ID="$exerciseId" -e USER_OFFSET="$userOffset" \
  loadimpact/k6 run /src/"$tests".js 2>&1)

echo "########## FINISHED testing - evaluating result ##########"
echo "$result"
if echo "$result" | grep -iqF error; then
  echo "################### ERROR in Quiz tests ###################"
  exit 1
fi

echo "######### SUCCESS Quiz tests finished without errors #########"
exit 0
