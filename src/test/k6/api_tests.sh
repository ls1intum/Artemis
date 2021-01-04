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
    -te|--timeoutExercise) # Timeout before creating the exercise in seconds
      timeoutExercise=$2
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
    -au|--admin-username)
      adminUsername=$2
      shift 2
      ;;
    -ap|--admin-password)
      adminPassword=$2
      shift 2
      ;;
    -cu|--createUsers)
      createUsers=true
      shift 1
      ;;
    -cl|--cleanup)
      cleanup=true
      shift 1
      ;;
    --tests)
      tests=$2
      shift 2
      ;;
    -pl|--programming-language)
      programmingLanguage=$2
      shift 2
      ;;
    -uo|--user-offset)
      userOffset=$2
      shift 2
      ;;
    -ci|--course-id)
      courseId=$2
      shift 2
      ;;
    -ei|--exercise-id)
      exerciseId=$2
      shift 2
      ;;
    -wqs|--wait-quiz-start)
      waitQuizStart=true
      shift 1
      ;;
    -op|--only-prepare)
      onlyPrepare=true
      shift 1
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
adminUsername=${adminUsername:?"You have to specify the username of one admin"}
adminPassword=${adminPassword:?"You have to specify the password of one admin"}
createUsers=${createUsers:-false}
cleanup=${cleanup:-false}
tests=${tests:?"You have to specify which tests to run"}
iterations=${iterations:-10}
timeoutParticipation=${timeoutParticipation:-60}
timeoutExercise=${timeoutExercise:-10}
programmingLanguage=${programmingLanguage:-"JAVA"}
userOffset=${userOffset:-0}
courseId=${courseId:-0}
exerciseId=${exerciseId:-0}
waitQuizStart=${waitQuizStart:-false}
onlyPrepare=${onlyPrepare:-false}

echo "################### STARTING API Tests ###################"
result=$(docker run -i --rm --network=host --name api-tests-"$tests"-"$programmingLanguage" -v "$baseDir":/src -e BASE_USERNAME="$baseUsername" -e BASE_URL="$baseUrl" \
  -e BASE_PASSWORD="$basePassword" -e ITERATIONS="$iterations" -e TIMEOUT_PARTICIPATION="$timeoutParticipation" -e CLEANUP="$cleanup" \
  -e ADMIN_USERNAME="$adminUsername" -e ADMIN_PASSWORD="$adminPassword" -e CREATE_USERS="$createUsers" -e TIMEOUT_EXERCISE="$timeoutExercise" \
  -e PROGRAMMING_LANGUAGE="$programmingLanguage" -e USER_OFFSET="$userOffset" -e COURSE_ID="$courseId" -e EXERCISE_ID="$exerciseId" \
  -e WAIT_QUIZ_START="$waitQuizStart" -e ONLY_PREPARE="$onlyPrepare" \
  loadimpact/k6 run --address localhost:0 /src/"$tests".js 2>&1)

echo "########## FINISHED testing - evaluating result ##########"
echo "$result"
if echo "$result" | grep -iqF FAILTEST; then
  echo "################### ERROR Server API tests failed ###################"
  exit 1
fi

echo "######### SUCCESS Server API tests finished #########"
exit 0
