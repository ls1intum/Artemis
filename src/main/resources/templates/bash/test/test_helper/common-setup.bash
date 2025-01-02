_common_setup() {
    bats_load_library "bats-support"
    bats_load_library "bats-assert"
    bats_load_library "bats-file"

    PROJECT_ROOT="$( cd "$BATS_TEST_DIRNAME/.." >/dev/null 2>&1 && pwd )"
    ASSIGNMENT_ROOT="$PROJECT_ROOT/${studentParentWorkingDirectoryName}"

    PATH="$ASSIGNMENT_ROOT:$PATH"
}

# _assert_file_contents
# ============
#
# Fail if the actual and expected file contents differ.
#
# Usage: _assert_file_contents <actual_path> <expected_path>
#
# IO:
#   STDERR - unified diff, on failure
# Options:
#   <actual_path>      The file being compared.
#   <expected_path>    The file to compare against.
_assert_file_contents() {
    if ! diff_output=$(diff -u --label="actual" --label="expected" "$1" "$2" 2>&1); then
        echo "$diff_output" \
        | batslib_decorate "$1: file contents differ" \
        | fail
    fi
}

# reduce output
bats_print_stack_trace() { :; }
bats_print_failed_command() { :; }
