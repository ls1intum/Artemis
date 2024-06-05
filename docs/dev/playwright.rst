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


Best practices for writing tests in Playwright
----------------------------------------------

1. **Use page objects for common interactions**:

    Page objects are a design pattern that helps to abstract the details of the page structure and interactions. They
    encapsulate the page elements and their interactions with the page. This makes the tests more readable and
    maintainable.
    Page objects are stored in the ``support/pageobjects`` folder. Each page object is implemented as a class containing
    a Playwright page instance and may have instances of other page objects as well. Page object classes provide methods
    performing common user actions or returning frequently used locators.
    Page objects are registered as fixtures to make them easily accessible in tests without caring about their
    initialization and teardown.

2. **Use fixtures**:

    Test fixture in Playwright is a setup environment that prepares the necessary conditions and state required for your
    tests to run. It helps manage the initialization and cleanup tasks so that each test starts with a known state.
    We use fixtures for all POMs and common test commands such as ``login``. Fixtures are defined in
    ``support/fixtures.ts``.

    To create a fixture, define its instance inside a corresponding existing type or define a new one:

    .. code-block:: typescript

        export type ArtemisPageObjects = {
            loginPage: LoginPage;
        }

    2. Ensure the base test (``base``) extends the fixture type. Define a fixture with the relevant name and return the
    desired instance as an argument of ``use()`` function as below:

    .. code-block:: typescript

        export const test = base.extend<ArtemisPageObjects>({
            loginPage: async ({ page }) => new LoginPage(page)
        });

    3. Inject the fixture to tests when needed as an argument to the ``test()`` function as follows:

    .. code-block:: typescript

        test('Test name', async ({ fixtureName }) => {
            // Test code
        });

3. **Use uniquely identifiable locators**:

    Use unique locators to identify elements on the page. Playwright throws an error when interacting with a locator
    that matches multiple elements on the page. To ensure uniqueness, use locators based on the element's
    ``data-testid``, ``id``, unique ``class`` or a combination of these attributes.

    Avoid using the ``nth()`` method or the ``nth-child`` selector, as they rely on the element’s position in the DOM
    hierarchy. Use these methods only when iterating over multiple similar elements.

    Avoid using locators that are prone to change. If a component lacks a unique selector,
    add a ``data-testid`` attribute with a unique value to its template. This ensures that the component is easily
    identifiable, making tests less likely to break when there are changes to the component.

4. **Consider actionability of elements**

    Checking for the state of an element before interacting with it is crucial to avoid flaky behavior. Actions like
    clicking a button or typing into an input field require a particular state from the element, such as visible and
    enabled, which makes it actionable.  Playwright ensures that the elements you interact with are actionable before
    performing such actions.

    However, some complex interactions may require additional checks to ensure the element is in the desired state. For
    example, consider a case where we want to access the inner text of an element that is not visible yet. Use ``waitFor()``
    function of a locator to wait for its ``visible`` state before accessing its inner text:

    .. code-block:: typescript

        await page.locator('.clone-url').waitFor({ state: 'visible' });
        const urlText = await this.page.locator('.clone-url').innerText();

    .. warning ::

        Avoid using ``page.waitForSelector()`` function to wait for an element to appear on the page. This function
        waits for the visibility in the DOM, but it does not guarantee that the element is actionable. Always
        prefer the ``waitFor()`` function of a locator instead.

    In some cases, we may need to wait for the page to load completely before interacting with its elements. Use
    ``waitForLoadState()`` function to wait for the page to reach a specified load state:

    .. code-block:: typescript

        await page.waitForLoadState('load');

    .. warning ::

        Waiting for the page load state is not recommended if we are only interested in specific elements appearing on
        the page - use ``waitFor()`` function of a locator instead.

