## Test Repository instructions

#### Test tool & command
Tests will be run using the command `./gradlew clean test`.

#### Project structure
Make sure that the package structure of your test classes is equivalent to the package structure of your base and solution repository.
Otherwise during the test run Gradle will not be able to find the imported classes in your test files.

In order to use classes from the template or solution repository in a test case, you have to add the respective repository
files as a dependency to the test project. Follow this description for IntelliJ:

1. Open the Test-Project first
2. Select `File` -> `New` -> `Module from Existing Source`. Then choose the template project
root folder. Select the option `Import module from external model` and `Gradle` and press `Finish`.
3. Repeat the previous step for the Solution-Project.
4. Add the following line to `settings.gradle` inside the Test-Project:
`includeBuild '<template-project-root-path'>` or `includeBuild '<solution-project-root-path>'` depending on which project you
want to use the source files for executing the test cases.
5. Inside the `plugin`-block in the `build.gradle` inside the Test-Project, add the following line:
`testImplementation(':${exerciseNamePomXml}-Exercise')` or
`testImplementation(':${exerciseNamePomXml}-Solution')` depending on which project you want to use the source files for executing
the test cases.

**Note**: Changes made in step 4 and step 5 must not be pushed to the remote repository as it can only be used
for editing all three projects in the local IDE.

#### Sequential test runs
If you have decided to use the sequential test runs feature for this exercise, read the following instructions:
We use the folder structure of the test repository to differentiate structural and behavior tests:
1. Structural test files must be placed in the folder "structural"
2. Behavior test files must be placed in the folder "behavior"

Files in other folders will not be executed!

#### Static Code Analysis
The build.gradle contains dependencies for the execution of static code analysis, if the option is active for this programming exercise.
