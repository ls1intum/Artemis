# Cypress setup
This test suite only contains system tests. Therefore the test suite cannot be run out of the box and has some requirements. Those will be listed in the following.

## Requirements
1. Running (and reachable) test environment. The Artemis Server and Client have to be deployed somewhere, where the tests can access them. This can be a local setup or a remote one (the Artemis test servers for example)
2. Pre-created users on the test environment. See the [corresponding section](#pre-created-users) below

### Pre-created users
Currently the test suite does not automatically create the required users with their roles, but expects existing ones. There are several users the tests require:
|        ROLE        |     ID(s)     | AMOUNT |
|:------------------:|:-------------:|:------:|
|        `ADMIN`       |       -       |    1   |
|        `USER`        | 100, 102, 104 |    3   |
| `TEACHING ASSISTANT` |      101      |    1   |
|     `INSTRUCTOR`     |      103      |    1   |

The test suite expects two templates in its configuration, which contain the text "USERID". When authenticating as one of the non-admin users the test will substitute the `USERID` text in the username and password templates with the required ID of the user.

For example a basic template for usernames and passwords could be `user_USERID` and `password_USERID`. A test, which needed to authenticate as `INSTRUCTOR` would then user the credentials `user_103`:`password_103`.

A user with the `ADMIN` role is needed to create and delete courses. That account does not have an `ID` because the template is not used for that user. The credentials for the `ADMIN` account are provided separately in the configuration of the test suite.

:warning: **The guided tour should be disabled for every user required by the test suite. Otherwise some/all tests in the suite will fail!**

### Test suite configuration
Once the test environment is running and the required users are added in the test environment the test suite needs to be configured. The configuration is done via two files:
1. `cypress.json`: Contains general settings for Cypress (e.g. the `baseUrl` pointing to the test environment)

## Using cypress
* Start the client and server of Artemis (if you are running the client on a different port than 8080 adjust the baseUrl in the Artemis/cypress.json file)
* Adjust the (admin-) username and password in Artemis/cypress.env.json
* Run `` npm run cypress:open `` to open the cypress dashboard
* In the dashboard you can run every spec file individually by clicking on it. This will open a new browser, which will execute the test
* Alternatively you can run cypress in headless mode by running `` npm run cypress:run ``. This won't open the cypress dashboard, will run all test files in a headless browser and will record the test runs. The recording files can be found under 'src/test/cypress/videos' and 'src/test/cypress/screenshots'
