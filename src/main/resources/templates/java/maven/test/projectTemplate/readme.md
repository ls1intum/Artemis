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

#### Static Code Analysis
The pom.xml contains dependencies for the execution of static code analysis, if the option is active for this programming exercise.

#### Maven project
This template is not only using maven for the tests, but also within the template provided to the students.
Make sure that your students understand that they have to import this project as maven project as otherwise errors occur.
If you add dependencies to the "pom.xml" file of the exercise, also make sure to add them to the "pom.xml" of the tests as otherwise the test have missing dependencies.
