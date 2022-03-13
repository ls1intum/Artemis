## Test Repository instructions

#### Test tool & command
Tests will be run using the command ./gradlew clean test.

#### Project structure
Make sure that the package structure of your test classes is equivalent to the package structure of your base and solution repository.
Otherwise during the test run Gradle will not be able to find the imported classes in your test files.

In order to use classes from the template or solution repository in a test case, you have to add the respective repository
files as a dependency to the test project. Follow this description for IntelliJ:

"File" -> "Project Structure..." -> "Modules" -> "&lt;ProjectName>-Tests" -> Sub-Option "test" -> "Dependencies" -> Button
"+" -> "3. Module Dependency" -> Select the respective repository name with an appended ".main".

**Note**: For sequential test projects, you have to select the respective repository name with an appended ".behavior".

#### Sequential test runs
If you have decided to use the sequential test runs feature for this exercise, read the following instructions:
We use the folder structure of the test repository to differentiate structural and behavior tests:
1. Structural test files must be placed in the folder "structural"
2. Behavior test files must be placed in the folder "behavior"

Files in other folders will not be executed!

#### Static Code Analysis
The build.gradle contains dependencies for the execution of static code analysis, if the option is active for this programming exercise.
