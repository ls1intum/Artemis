## Test Repository instructions

#### Test tool & command
Tests will be run using the command maven clean test.

#### Project structure
Please make sure that the package structure of your test classes is equivalent to the package structure of your base and solution repository.
Otherwise during the test run maven will not be able to find the imported classes in your test files.

#### Sequential test runs
If you have decided to use the sequential test runs feature for this exercise, please read the following instructions:
We use a file pattern to run structural before behavior tests.
1. Structural test files must contain the string "Structural" in their file / class name, e.g. ClassStructuralTest.java
2. Behavior test files must contain the string "Behavior" in their file / class name, e.g. SortingBehaviorTest.java

All other files that don't follow any of the described patterns will NOT be executed!
