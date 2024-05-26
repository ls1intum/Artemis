E2E Testing with Playwright
===========================

Set up Playwright locally
-------------------------

To run the tests locally, developers need to set up Playwright on their machine.
End-to-end tests test entire workflows, therefore they require an entire Artemis setup - database, client and server
to be running.
Playwright tests rely on the Playwright Node.js library, Playwright browser extensions and some helper packages.

1. Install dependencies:

    First navigate to the Playwright folder using ``cd src/test/playwright``. Then run ``npm install``.

2. Customize Playwright configuration:

    We need to configure Playwright to match our local Artemis setup and user settings. All configurations are stored in
    the ``playwright.env`` file. Default configuration for ICL setup looks as follows:

    .. code-block:: text

        PLAYWRIGHT_USERNAME_TEMPLATE=artemis_test_user_USERID
        PLAYWRIGHT_PASSWORD_TEMPLATE=artemis_test_user_USERID
        ADMIN_USERNAME=artemis_admin
        ADMIN_PASSWORD=artemis_admin
        ALLOW_GROUP_CUSTOMIZATION=true
        STUDENT_GROUP_NAME=students
        TUTOR_GROUP_NAME=tutors
        EDITOR_GROUP_NAME=editors
        INSTRUCTOR_GROUP_NAME=instructors
        CREATE_USERS=true
        BASE_URL=http://localhost:9000
        EXERCISE_REPO_DIRECTORY=test-exercise-repos

    Make sure ``BASE_URL`` matches your Artemis client URL and ``ADMIN_USERNAME`` and
    ``ADMIN_PASSWORD`` match your Artemis admin user credentials. If you want to create users for the tests, set
    ``CREATE_USERS`` to ``true``.

3. Configure test users

     Playwright tests require users with different roles to simulate concurrent user interactions. You can configure
     user ids and check their corresponding user roles in ``src/test/playwright/support/users.ts`` file. Usernames are
     defined automatically by replacing the ``USERID`` part in ` ``PLAYWRIGHT_USERNAME_TEMPLATE`` with corresponding
     user id. If users with such usernames do not exist, set ``CREATE_USERS`` to ``true`` on the ``playwright.env``
     file for users to be created on setup stage. If there exist users with the same name(s) but different user role(s),
     change user ids to different values to ensure new users are created with roles as defined in configuration.

4. Setup Playwright package and its browser extensions:

    Run ``npm run playwright:setup`` to install Playwright browser binaries, set up the environment to ensure Playwright
    can locate these binaries and create test users if creating users is enabled in configuration.

5. Open Playwright UI

    Run ``npm run playwright:open`` to open the Playwright UI. This is a graphical interface that allows you to run
    individual tests, test files or test suites.
    Another way to run tests is to use the command ``npm run playwright:run``. This command runs all tests in command
    line.

Test parallelization
--------------------

Running tests in parallel may speed up test execution. Playwright tests are configured to run in fully parallel mode
by default. It means all tests in all files are executed in parallel. This is achieved by Playwright's built-in
parallelization feature. Test execution tasks are divided among worker processes. Each process runs a separate browser
instance and executes a subset of tests. The number of worker processes can be adjusted in ``playwright.config.js``
file.

.. warning ::
    Using more worker processes divides the available computing resources, giving each worker fewer resources. If you
    use too many workers, it can lead to resource contention, slowing down individual test execution and potentially
    causing timeouts.

To make tests run sequentially, set the ``workers`` option to ``1``. If you want tests in a single file to run
sequentially and only test files to run in parallel, set the ``fullyParallel`` option to ``false``.
