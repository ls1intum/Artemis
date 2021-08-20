#!/bin/bash

# copy code from the assignment or solution to the appropriate test folder
cp_code()
{
    cd "$1" || exit
    rm ./*.ml >/dev/null 2>&1
    # shellcheck disable=SC2086
    ln -s $BUILD_ROOT/$1/src/*.ml ./
    cd ..
}

OUTPUT_FILE="test-reports/results.xml"
BUILD_TIMEOUT="1m"
TEST_TIMEOUT="10m"
SAFE=false
BUILD_ROOT=$(realpath ..)

# run hidden tests before this date, this should be somtimes before the release of the exercise
HIDDEN_START=$(date -d '2021-08-01 00:00 CEST' +%s)
# run hidden tests after this date, this should be somtimes after the deadline and before the run of the after due tests
HIDDEN_END=$(date -d '2021-08-20 00:15 CEST' +%s)
NOW=$(date +%s)
RUN_HIDDEN=false

if [ "$HIDDEN_START" -gt "$NOW" ] || [ "$NOW" -gt "$HIDDEN_END" ]; then
    RUN_HIDDEN=true
fi

# check passed flags
while getopts s opt; do
  case $opt in
    s) SAFE=true;;
    *)
  esac
done

# check for symlink is the submission
find ../assignment/ -type l | grep -q . && echo "Cannot build with symlinks in submission." && exit 0

# include solution and assignment in the tests
# this will only pick up *.ml files in the /src folders if other files are required for the tests this needs to be adjusted
cp_code solution
echo 'include Assignment' > solution/solution.ml
cp_code assignment

# select if tests are run by generated source code as studen toplevel code may run before the tests and be able to spoof a runtime signal
echo "let runHidden = $RUN_HIDDEN" > test/runHidden.ml

# shellcheck disable=SC2046
eval $(opam env)

# build the sudent submission
# don't reference the tests or solution, so that we can show the build output to the sudent and not leak test / solution code
if ! timeout -s SIGTERM $BUILD_TIMEOUT dune build --force assignment; then
    echo "Unable to build submission, please ensure that your code builds and matches the provided interface" >&2
    exit 0
fi
# If there are build failures, the compiler sometimes prints source code of tests to stderr by default, which is shown to the participant.
# Therefore, drop stderr output. If the student submission builds and matches the interface, this should never fail
if ! timeout -s SIGTERM $BUILD_TIMEOUT dune build --force test >/dev/null 2>/dev/null; then
    echo "Unable to build tests, please report this failure to an instructor" >&2
    exit 0
fi

cd "$BUILD_ROOT" || exit

# copy the test executable into the project root
mv -f tests/test/test.exe ./

# to then delete all source code, to prevent access to it while running the code
if $SAFE; then
    rm -rf assignment
    rm -rf solution
    rm -rf tests
fi;

# Run the test
mkdir "$BUILD_ROOT"/test-reports
timeout -s SIGTERM $TEST_TIMEOUT ./test.exe -output-junit-file "test-reports/results.xml"

if [ $? = 124 ]; then # timeout exits with 124 if it had to kill the tests.
    echo -e "Testing your submission resulted in a timeout." 1>&2
# Warn the participant in case no output file could be generated
elif [ ! -f "$OUTPUT_FILE" ]; then
    echo -e "Your submission could not be built.\nPlease check whether your submission compiles and whether all functions and constants have the type specified in the problem statement." 1>&2
fi

# We always exit this script with error code 0, so that the following steps on Bamboo are performed.
exit 0
