setup() {
    load "test_helper/common-setup"
    _common_setup
}

@test "hello world" {
    run exercise.bash
    assert_output "Hello World!"
}
