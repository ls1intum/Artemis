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

# Create the 'supporting_scripts/test-results' directory, if it doesn't exist.
mkdir -p supporting_scripts/test-results

for ((run = 1; run <= NUM_RUNS; run++)); do
    # Generate a random number between 1 and 13.
    spring_profile_chance=$((RANDOM % 13 + 1))

    # Determine the active spring profile based on the random number 10/13 chance of no profile, 2/13 chance of MYSQL, 1/13 chance of POSTGRES.
    profile_index=0
    if [[ $spring_profile_chance -gt 10 ]]; then
        profile_index=1
    fi

    if [[ $spring_profile_chance -gt 12 ]]; then
        profile_index=2
    fi
    active_profile="${SPRING_PROFILES[$profile_index]}"

    # Generate a random number of maximum concurrent tests between 1 and 6
    max_concurrent_tests=$((RANDOM % 6 + 1))

    # Generate output file name
    TIME=$(date +"%Y-%m-%d_%H:%M:%S")
    output_file="${active_profile}_${TIME}_threads${max_concurrent_tests}_run${run}.log"

    # Run tests with gradlew
    echo "Running tests with Spring Profile: $active_profile, Threads: $max_concurrent_tests (Run $run)"
    set -o pipefail && SPRING_PROFILES_INCLUDE="$active_profile" ./gradlew --console=plain \
        -Djunit.jupiter.execution.parallel.config.fixed.parallelism="$max_concurrent_tests" \
        test --rerun jacocoTestReport -x webapp jacocoTestCoverageVerification > "./supporting_scripts/test-results/$output_file"

    # Check if tests were successful
    if grep -q "BUILD SUCCESSFUL" "./supporting_scripts/test-results/$output_file"; then
        # TODO: Remove file, if the test run was successful
        mv "./supporting_scripts/test-results/$output_file" "./supporting_scripts/test-results/SUCCESS_${output_file}"
    else
        mv "./supporting_scripts/test-results/$output_file" "./supporting_scripts/test-results/FAILURE_${output_file}"
    fi

    # Wait a bit before the next run
    sleep 10
done

# TODO: Create a summary of failing tests, including the number of times they failed and the corresponding file names

echo "Tests completed for $NUM_RUNS run(s)."
