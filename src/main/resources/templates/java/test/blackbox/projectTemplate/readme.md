## Test Repository instructions

#### Project structure
The `testsuite` directory contains the test definitions used by DejaGnu.
`config/default.exp` contains the base setup shared by all tests.
The actual tests are split up into `public.exp`, `advanced.exp`, and `secret.exp`.

In the `testfiles/` additional support files can be provided that can be loaded by the program under test.
The `secret/` directory can contain files that should only be readable during execution of the `secret` tests, but not during the other two.

To make the `secret` tests actually secret (i.e. results not visible to the student), you need to configure the test visibility in the grading configuration of the exercise.

#### Static Code Analysis
The pom.xml contains dependencies for the execution of static code analysis, if the option is active for this programming exercise.
