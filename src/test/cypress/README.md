# Artemis Cypress test suite
This folder contains the End-to-End testing test suite for Artemis.
The test suite only contains system tests. Therefore it cannot be run out of the box and has some requirements. Those will be listed in the following.

# Requirements
1. Running (and reachable) test environment. The Artemis Server and Client have to be deployed somewhere, where the tests can access them. This can be a local setup or a remote one (the Artemis test servers for example)
2. Pre-created users on the test environment. See the [corresponding section](#pre-created-users) below
3. Node and Chrome installed on the machine, which executes the test suite

## Pre-created users
Currently the test suite does not automatically create the required users with their roles, but expects existing ones. There are several users the tests require:
|        ROLE        |     ID(s)     | AMOUNT |
|:------------------:|:-------------:|:------:|
|        `ADMIN`       |       -       |    1   |
|        `USER`        | 100, 102, 104 |    3   |
| `TEACHING ASSISTANT` |      101      |    1   |
|     `INSTRUCTOR`     |      103      |    1   |

The test suite expects two templates (one for usernames and one for passwords) in its configuration, which contain the text `USERID`. When authenticating as one of the non-admin users the test will substitute the `USERID` text in the username and password templates with the required ID of the user.

For example a basic template for usernames and passwords could be `user_USERID` and `password_USERID`. A test, which needed to authenticate as `INSTRUCTOR` would then use the credentials `user_103`:`password_103` for authentication.

A user with the `ADMIN` role is needed to create and delete courses. That account does not have a fixed `ID` because the template is not used for that user. The credentials for the `ADMIN` account are provided separately in the configuration of the test suite.

:warning: **The guided tour should be disabled for every user required by the test suite. Otherwise some/all tests in the suite will fail!**

## Test suite configuration
### General configuration
Once the test environment is running and the required users are added in the test environment the test suite needs to be configured. The configuration is done via two files:
1. `cypress.json`: Contains general settings for Cypress
2. `cypress.env.json`: Contains settings specific for Artemis

In the following we will explain what setting in the configuration files has to be adjusted to be able to execute the test suite:
For `cypress.json`:
* `baseUrl`: The url pointing to the test environment here (make sure that there is no trailing slash)

For `cypress.env.json`:
* `username`: The username template with the `USERID` here (as described in the [pre-created users section](#pre-created-users))
* `password`: The password template
* `adminUsername`: The admin username (no template)
* `adminPassword`: The admin password (no template)

### Additional configuration for test environments using the Bamboo + Bitbucket setup
On Bamboo + Bitbucket setups Artemis has issues with the group synchronization if a new course is created and immediately afterwards a programming exercise is created. This requires the tests to wait for over one minute in each test spec, which is related to programming exercises. This increases the total execution time of the test suite by a lot. The issue can be avoided by using already existing user groups in the course creation.

Currently the tests require one pre-created user group for the roles `USER`, `TEACHING ASSISTANT`, `EDITOR` and `INSTRUCTOR` in the user management system of the test environment. Each group has its own setting in the configuration file. The `allowGroupCustomization` setting has to be set to `true` otherwise the tests will not use the pre-created user groups (resulting in a failure of all programming exercise related tests on a Bamboo + Bitbucket setup).
* `allowGroupCustomization`: `true`
* `studentGroupName`: The group name for students (e.g. `artemis-e2etest-students`)
* `tutorGroupName`: The group name for tutors (e.g. `artemis-e2etest-tutors`)
* `editorGroupName`: The group name for tutors (e.g. `artemis-e2etest-editors`)
* `instructorGroupName`: The group name for tutors (e.g. `artemis-e2etest-instructors`)

### Example configurations
In the following we will show example configurations of the test suite for imaginary Artemis setups. For readability we will leave out default settings, which are not mandatory to be adjusted.
#### Test environment using Gitlab + Jenkins
`cypress.json`:
```json
{
    "baseUrl": "https://imaginary-artemis-server.com",
}
```
`cypress.env.json`:
```json
{
    "username": "username_USERID",
    "password": "password_USERID",
    "adminUsername": "admin_username",
    "adminPassword": "admin_password",
}

```
#### Test environment using Bamboo + Bitbucket
`cypress.json`:
```json
{
    "baseUrl": "https://imaginary-artemis-server.com",
}
```
`cypress.env.json`:
```json
{
    "username": "username_USERID",
    "password": "password_USERID",
    "adminUsername": "admin_username",
    "adminPassword": "admin_password",
    "allowGroupCustomization": true,
    "studentGroupName": "artemis-e2etest-students",
    "tutorGroupName": "artemis-e2etest-tutors",
    "editorGroupName": "artemis-e2etest-editors",
    "instructorGroupName": "artemis-e2etest-instructors"
}

```

# Executing the test suite
Before executing the test suite Cypress with all of its dependencies has to be installed via `npm`. Make sure that the command is executed in the Cypress subfolder of Artemis (`src/test/cypress`).
```bash
npm install

```
The test suite can be executed with via the predefined commands in the `package.json`:
1. `npm run cypress:open`: This opens a Cypress Dashboard from where all or individual tests can be executed
2. `npm run cypress:run`: This executes the complete test suite in headless mode

Individual test specs in the test suite can be executed by passing the `--spec` parameter like so:
```bash
npm run cypress:run -- --spec integration/path/to/spec/file.spec.ts 

```

For more information about Cypress and its configuration see the [Cypress documentation](https://docs.cypress.io/guides/getting-started/installing-cypress)

