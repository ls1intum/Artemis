E2E Testing with Playwright
===========================

Set up Playwright locally
-------------------------

To run the tests locally, developers need to set up Playwright on their machines.
End-to-end tests test entire workflows; therefore, they require the whole Artemis setup - database, client, and server to be running.
Playwright tests rely on the Playwright Node.js library, browser binaries, and some helper packages.

1. Install dependencies:

    First, navigate to the Playwright folder:

    .. code-block:: bash

        cd src/test/playwright

    Then install the dependencies:

    .. code-block:: bash

        npm install

2. Customize Playwright configuration:

    We need to configure Playwright to match our local Artemis setup and user settings. All configurations are stored in
    the ``playwright.env`` file. The default configuration for an ICL setup looks as follows:

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
    ``ADMIN_PASSWORD`` match your Artemis admin user credentials.

3. Configure test users

     Playwright tests require users with different roles to simulate concurrent user interactions. You can configure
     user IDs and check their corresponding user roles in the ``src/test/playwright/support/users.ts`` file. Usernames
     are defined automatically by replacing the ``USERID`` part in ``PLAYWRIGHT_USERNAME_TEMPLATE`` with the
     corresponding user ID. If users with such usernames do not exist, set ``CREATE_USERS`` to ``true`` on the
     ``playwright.env`` file for users to be created during the setup stage. If users with the same usernames but
     different user roles already exist, change the user IDs to different values to ensure that new users are created
     with roles defined in the configuration.

4. Setup Playwright package and its browser binaries:

    Install Playwright browser binaries, set up the environment to ensure Playwright can locate these binaries, and
    create test users (if creating users is enabled in the configuration) with the following command:

    .. code-block:: bash

        npm run playwright:setup

5. Open Playwright UI

    To open the Playwright UI, run:

    .. code-block:: bash

        npm run playwright:open

    This opens a graphical interface that allows you to run individual tests, test files, or test suites while observing
    the test execution in a browser window.

    Another way to run tests is through the command line. To run all tests in the command line, use:

    .. code-block:: bash

        npm run playwright:test

    To run a specific test file, use:

    .. code-block:: bash

        npx playwright test <path_to_test_file>

    If you want to run a specific test suite or a single test, add the ``-g`` flag to the previous command, followed by the
    test suite name or test name.
    For example, you can run the test suite "`Course creation`" located in the file ``CourseManagement.spec.ts`` using
    the command:

    .. code-block:: bash

        npx playwright test src/test/playwright/tests/CourseManagement.spec.ts -g "Course creation"

Test parallelization
--------------------

Running tests in parallel may speed up test execution. We achieve this using Playwright's built-in parallelization
feature. By default, tests are configured to run in fully parallel mode. This means that all tests in all files are
executed in parallel. Test execution tasks are divided among worker processes. Each process runs a separate browser
instance and executes a subset of tests. The number of worker processes can be adjusted in the ``playwright.config.js``
file.

.. warning ::
    Using more worker processes divides the available computing resources, giving each worker fewer resources. Using too
    many workers can lead to resource contention, slowing down individual test execution and potentially causing
    timeouts.


To run tests sequentially (one after another), set the ``workers`` option to ``1``. To run tests within each file
sequentially, while running test files in parallel, set the ``fullyParallel`` option to ``false``.
