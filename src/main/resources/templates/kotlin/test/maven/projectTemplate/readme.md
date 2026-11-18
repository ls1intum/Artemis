## Test Repository instructions

#### Test tool & command
Tests will be run using the command maven clean test.

#### Project structure
Make sure that the package structure of your test classes is equivalent to the package structure of your base and solution repository.
Otherwise during the test run maven will not be able to find the imported classes in your test files.

#### Sequential test runs
If you have decided to use the sequential test runs feature for this exercise, read the following instructions:
We use the folder structure of the test repository to differentiate structural and behavior tests:
1. Structural test files must be placed in the folder "structural"
2. Behavior test files must be placed in the folder "behavior"

Files in other folders will not be executed!
