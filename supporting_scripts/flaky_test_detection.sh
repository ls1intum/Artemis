#!/bin/bash

# Check for the number of test runs argument.
if [ $# -eq 0 ]; then
    echo "Usage: $0 <number_of_runs>"
    exit 1
fi

# Get the number of test runs from the command line argument.
NUM_RUNS="$1"

# Define spring profiles.
SPRING_PROFILES=("none" "mysql" "postgres")

DIRECTORY='./build/flaky-test-detection-results'

# Create the directory, if it doesn't exist.
mkdir -p ${DIRECTORY}

# ==================== #
# FLAKY TEST DETECTION #
# ==================== #

for ((run = 1; run <= NUM_RUNS; run++)); do
    # Generate a random number between 1 and 13.
    spring_profile_chance=$((RANDOM % 13 + 1))

    # Determine the active spring profile based on the random number:
    # 10/13 chance of no profile, 2/13 chance of MYSQL, 1/13 chance of POSTGRES.
    # (Should result in similar execution times for each profile).
    profile_index=0
    if [[ $spring_profile_chance -gt 10 ]]; then
        profile_index=1
    fi

    if [[ $spring_profile_chance -gt 12 ]]; then
        profile_index=2
    fi
    active_profile="${SPRING_PROFILES[$profile_index]}"

    # Generate output file name
    TIME=$(date +"%Y-%m-%d_%H:%M:%S")
    output_file="${active_profile}_${TIME}_run${run}.log"

    # Run tests with gradlew
    echo "Running tests with Spring Profile: $active_profile (Run $run)"
    set -o pipefail && SPRING_PROFILES_INCLUDE="$active_profile" ./gradlew --console=plain \
        test --rerun jacocoTestReport -x webapp jacocoTestCoverageVerification > "${DIRECTORY}/$output_file"

    # Check if tests were successful. If not, rename the output file to indicate failure. Delete the output file if tests were successful.
    if grep -q "BUILD SUCCESSFUL" "${DIRECTORY}/$output_file"; then
        rm "${DIRECTORY}/$output_file"
    else
        mv "${DIRECTORY}/$output_file" "${DIRECTORY}/FAILURE_${output_file}"
    fi

    # Wait a bit before the next run
    sleep 10
done

# ================== #
# FLAKY TEST SUMMARY #
# ================== #

echo "Generating flaky test summary..."

SUMMARY_DIRECTORY="${DIRECTORY}/summary"
mkdir -p "$SUMMARY_DIRECTORY"

SUMMARY_FILE="${SUMMARY_DIRECTORY}/run-summary.txt"
printf "Logfile and Failed Tests\n" > "$SUMMARY_FILE"
for file in "$DIRECTORY"/*.log; do
    if [ -f "$file" ]; then
        printf "\nFailed tests in $(basename "$file"):\n" >> "$SUMMARY_FILE"
        grep "Test >.* FAILED" "$file" >> "$SUMMARY_FILE"
    fi
done

COUNT_FILE="${SUMMARY_DIRECTORY}/failure-count.txt"
printf "Count of Failed Tests\n" > "$COUNT_FILE"
grep "Test >.* FAILED" "$SUMMARY_FILE" | sort | uniq -c | sort -nr >> "$COUNT_FILE"

echo "Tests completed for $NUM_RUNS run(s)."
