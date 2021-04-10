#!/bin/bash

OUTPUT_FOLDER="test-reports"
OUTPUT_FILE="$OUTPUT_FOLDER/results.xml"
ERROR_LOG="build_errors.txt"
TIMEOUT="5m"  # Timeout for running all tests
VERBOSE=false # Set to true to print out compile errors. Be aware that compile errors might show test code.

eval $(opam env)

# Run the test
# If there are build failures, the compiler sometimes prints source code of tests to stderr by default, which is shown to the participant.
# Therefore, hide stderr output by rederecting it to a file.
timeout -s SIGTERM $TIMEOUT dune runtest --build-dir=$OUTPUT_FOLDER 2>$ERROR_LOG

if [ $? = 124 ]; then # timeout exits with 124 if it had to kill the tests.
    echo -e "Testing your submission resulted in a timeout." 1>&2
# Warn the participant in case no output file could be generated
elif [ ! -f "$OUTPUT_FILE" ]; then
    echo -e "Your submission could not be built.\nPlease check whether your submission compiles and whether all functions and constants have the type specified in the problem statement." 1>&2

    if $VERBOSE ; then
        cat $ERROR_LOG 1>&2
    fi
fi

# We always exit this script with error code 0, so that the following steps on Bamboo are performed.
exit 0
