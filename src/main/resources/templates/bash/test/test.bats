setup_file() {
    BATS_TEST_TIMEOUT=10
}

setup() {
    load "test_helper/common-setup"
    _common_setup

    TEST_DATA="$BATS_TEST_DIRNAME/test_data"

    cp "$TEST_DATA"/{numbers.txt,rename_me.txt} "$BATS_TEST_TMPDIR"

    cd "$BATS_TEST_TMPDIR"
    touch delete_me.txt
    touch .hidden
}

@test "shebang" {
    first_line=$(head -n 1 "$ASSIGNMENT_ROOT/script.bash")
    assert_regex "$first_line" '^#!(/usr)?/bin/(env )?bash$'
}

@test "shebang_custom_message" {
    first_line=$(head -n 1 "$ASSIGNMENT_ROOT/script.bash")

    if ! assert_regex "$first_line" '^#!(/usr)?/bin/(env )?bash$' 2>/dev/null; then
        echo "$first_line" \
        | batslib_decorate "first line is not a valid shebang" \
        | fail
    fi
}

@test "list_dir" {
    run script.bash

    assert_output --partial delete_me.txt
    assert_output --partial numbers.txt
    assert_output --partial rename_me.txt
    assert_output --partial .hidden
}

@test "file_creation" {
    run script.bash

    assert_file_exists create_me.txt
}

@test "file_deletion" {
    run script.bash

    assert_file_not_exists delete_me.txt
}

@test "rename" {
    run script.bash

    assert_file_not_exists rename_me.txt
    assert_file_exists renamed.txt
    _assert_file_contents renamed.txt "$TEST_DATA/rename_me.txt"
}

@test "replace" {
    run script.bash

    _assert_file_contents numbers.txt "$TEST_DATA/numbers_expected.txt"
}

@test "status_code" {
    run script.bash

    assert_success
}
