_common_setup() {
    load "/usr/lib/bats/bats-support/load"
    load "/usr/lib/bats/bats-assert/load"
    # get the containing directory of this file
    # use $BATS_TEST_FILENAME instead of ${BASH_SOURCE[0]} or $0,
    # as those will point to the bats executable's location or the preprocessed file respectively
    PROJECT_ROOT="$( cd "$( dirname "$BATS_TEST_FILENAME" )/.." >/dev/null 2>&1 && pwd )"
    # make executables in ${studentParentWorkingDirectoryName}/ visible to PATH
    PATH="$PROJECT_ROOT/${studentParentWorkingDirectoryName}:$PATH"
}
