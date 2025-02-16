E2E Testing with Playwright
===========================

**Background**

The Playwright test suite contains system tests verifying the most important features of Artemis.
System tests test the whole system and therefore require a complete deployment of Artemis first.
In order to prevent as many faults (bugs) as possible from being introduced into the develop branch,
we want to execute the Playwright test suite whenever new commits are pushed to a Git branch
(just like the unit and integration test suites).

To accomplish this we need to be able to dynamically deploy multiple different instances of Artemis at the same time.
An ideal setup would be to deploy the whole Artemis system using Kubernetes.
However, this setup is too complex at the moment.
The main reason for the complexity is that it is very hard to automatically setup Docker containers for
the external services (e.g. Gitlab, Jenkins) and connect them directly with Artemis.

Therefore, the current setup only dynamically deploys the Artemis server and configures it to connect to
the prelive system, which is already properly setup in the university data center.

Set up Playwright locally
-------------------------

To run the tests locally, developers need to set up Playwright on their machines.
End-to-end tests test entire workflows; therefore, they require the whole Artemis setup - database, client, and server to be running.
Playwright tests rely on the Playwright Node.js library, browser binaries, and some helper packages.
To run playwright tests locally, you need to start the Artemis server and client, have the correct users set up and install and run playwright.
This setup should be used for debugging, and creating new tests for your code, but needs intellij to work, and relies on fully setting up your local Artemis instance
following :ref:`the server setup guide<dev_setup>`.


For a quick test setup with only three steps, you can use the scripts provided in `supportingScripts/playwright`.
The README explains what you need to do.
It sets up Artemis inside a dockerized environment, creates users and directly starts playwright. The main drawback with this setup is, that you cannot
easily change the version of Artemis itself.


If you want to manually install playwright, you can follow these steps:

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

        PLAYWRIGHT_USERNAME_TEMPLATE=artemis_test_user_
        PLAYWRIGHT_PASSWORD_TEMPLATE=artemis_test_user_
        ADMIN_USERNAME=artemis_admin
        ADMIN_PASSWORD=artemis_admin
        ALLOW_GROUP_CUSTOMIZATION=true
        STUDENT_GROUP_NAME=students
        TUTOR_GROUP_NAME=tutors
        EDITOR_GROUP_NAME=editors
        INSTRUCTOR_GROUP_NAME=instructors
        BASE_URL=http://localhost:9000
        EXERCISE_REPO_DIRECTORY=test-exercise-repos
        FAST_TEST_TIMEOUT_SECONDS=45
        SLOW_TEST_TIMEOUT_SECONDS=180


    Make sure ``BASE_URL`` matches your Artemis client URL and ``ADMIN_USERNAME`` and
    ``ADMIN_PASSWORD`` match your Artemis admin user credentials.

3. Configure test users

     Playwright tests require users with different roles to simulate concurrent user interactions. If you already
     have generated test users, you can skip this step. Generate users with the help of the user creation scripts under the
     `supportingScripts/playwright` folder:

    .. code-block:: bash

        setupUsers.sh

    You can configure user IDs and check their corresponding user roles in the ``src/test/playwright/support/users.ts`` file.
    Usernames are defined automatically by appending the userId to the ``PLAYWRIGHT_USERNAME_TEMPLATE``.
    At the moment it is discouraged to change the template string, as the user creation script does not support other names yet.

4. Setup Playwright package and its browser binaries:

    Install Playwright browser binaries, set up the environment to ensure Playwright can locate these binaries.
    On some operating systems this might not work, and playwright needs to be manually installed via a package manager.

    .. code-block:: bash

        npm run playwright:setup-local
        npm run playwright:init



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


Best practices when writing new E2E tests
-----------------------------------------

**Understanding the System and Requirements**

Before writing tests, a deep understanding of the system and its requirements is crucial.
This understanding guides determining what needs testing and what defines a successful test.
The best way to understand is to consolidate the original system`s developer or a person actively working on this
component.

**Identify Main Test Scenarios**

Identify what are the main ways the component is supposed to be used. Try
the action with all involved user roles and test as many different inputs as
feasible.

**Identify Edge Test Scenarios**

Next to the main test scenarios, there are also edge case scenarios. These
tests include inputs/actions that are not supposed to be performed (e.g. enter
a too-long input into a field) and test the error-handling capabilities of the
platform.

**Write Tests as Development Progresses**

Rather than leaving testing until the end, write tests alongside each piece of
functionality. This approach ensures the code remains testable and makes
identifying and fixing issues as they arise easier.

**Keep Tests Focused**

Keep each test focused on one specific aspect of the code. If a test fails, it is
easier to identify the issue when it does not check multiple functionalities at
the same time.

**Make Tests Independent**

Tests should operate independently from each other and external factors like
the current date or time. Each test should be isolated. Use API calls for unrelated tasks, such as creating a
course, and UI interaction for the appropriate testing steps. This also involves
setting up a clean environment for every test suite.

**Use Descriptive Test Names**

Ensure each test name clearly describes what the test does. This strategy
makes the test suite easier to understand and quickly identifies which test
has failed.

**Use Similar Test Setups**

Avoid using different setups for each test suit. For example, always check
for the same HTTP response when deleting a course.

**Do Not Ignore Failing Tests**

If a test consistently fails, pay attention to it. Investigate as soon as possible
and fx the issue, or update the test if the requirements have changed.

**Regularly Review and Refactor Your Tests**

Tests, like code, can accumulate technical debt. Regular reviews for duplication,
unnecessary complexity, and other issues help maintain tests and enhance reliability.


Playwright testing best practices
---------------------------------

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


Artemis Deployment on Bamboo Build Agent
----------------------------------------
Every execution of the Playwright test suite requires its own deployment of Artemis.
The easiest way to accomplish this is to deploy Artemis locally on the build agent, which executes the Playwright tests.
Using ``docker compose`` we can start a MySQL database and the Artemis server locally on the build agent and
connect it to the prelive system in the university data center.

.. figure:: playwright/playwright_bamboo_deployment_diagram.svg
  :align: center
  :alt: Artemis Deployment on Bamboo Build Agent for Playwright

  Artemis Deployment on Bamboo Build Agent for Playwright

In total there are three Docker containers started in the Bamboo build agent:

1. MySQL

  This container starts a MySQL database and exposes it on port 3306.
  The container automatically creates a new database 'Artemis' and configures it
  with the recommended settings for Artemis.
  The Playwright setup reuses the already existing
  `MySQL docker image <https://github.com/ls1intum/Artemis/blob/develop/docker/mysql.yml>`__
  from the standard Artemis Docker setup.

2. Artemis Application

  The Docker image for the Artemis container is created from the already existing
  `Dockerfile <https://github.com/ls1intum/Artemis/blob/develop/docker/artemis/Dockerfile>`__.
  When the Bamboo build of the Playwright test suite starts, it retrieves the Artemis executable (.war file)
  from the `Artemis build plan <https://bamboo.ase.in.tum.de/browse/ARTEMIS-WEBAPP>`_.
  Upon creation of the Artemis Docker image the executable is copied into the image together with configuration files
  for the Artemis server.

  The main configuration of the Artemis server are contained in the
  `Playwright environment configuration files <https://github.com/ls1intum/Artemis/tree/develop/docker/artemis/config>`__.
  However, those files do not contain any security relevant information.
  Security relevant settings are instead passed to the Docker container via environment variables. This information is
  accessible to the Bamboo build agent via
  `Bamboo plan variables <https://confluence.atlassian.com/bamboo/bamboo-variables-289277087.html>`__.

  The Artemis container is also configured to
  `depend on <https://docs.docker.com/compose/compose-file/compose-file-v2/#depends_on>`__
  the MySQL container and uses
  `health checks <https://docs.docker.com/compose/compose-file/compose-file-v2/#healthcheck>`__
  to wait until the MySQL container is up and running.

3. Playwright

  Playwright offers a test environment `docker image <https://hub.docker.com/r/microsoft/playwright>`__
  to execute Playwright tests.
  The image contains Playwright browsers and browser system dependencies.
  However, Playwright itself is not included in the image.
  This is convenient for us because the image is smaller and the Artemis Playwright project requires
  additional dependencies to fully function.
  Therefore, the Artemis Playwright Docker container is configured to install all dependencies
  (using :code:`npm ci`) upon start. This will also install Playwright itself.
  Afterwards the Artemis Playwright test suite is executed.

  The necessary configuration for the Playwright test suite is also passed in via environment variables.
  Furthermore, the Playwright container depends on the Artemis container and is only started
  once Artemis has been fully booted.

**Bamboo webhook**

The Artemis instance deployed on the build agent is not publicly available to improve the security of this setup.
However, in order to get the build results for programming exercise submissions Artemis relies on a webhook from Bamboo
to send POST requests to Artemis.
To allow this, an extra rule has been added to the firewall allowing only the Bamboo instance in the prelive system
to connect to the Artemis instance in the build agent.

**Timing**

As mentioned above, we want the Playwright test suite to be executed whenever new commits are pushed to a Git branch.
This has been achieved by adding the
`Playwright build plan <https://bamboo.ase.in.tum.de/browse/ARTEMIS-AEPTMA1132>`__
as a `child dependency <https://confluence.atlassian.com/bamboo/setting-up-plan-build-dependencies-289276887.html>`__
to the `Artemis Build build plan <https://bamboo.ase.in.tum.de/browse/ARTEMIS-WEBAPP>`__.
The *Artemis Build* build plan is triggered whenever a new commit has been pushed to a branch.

The Playwright build plan is only triggered after a successful build of the Artemis executable.
This does imply a delay (about 10 minutes on average) between the push of new commits and the execution
of the Playwright test suite, since the new Artemis executable first has to be built.

**NOTE:** The Playwright test suite is only automatically executed for internal branches and pull requests
(requires access to this GitHub repository), **not** for external ones.
In case you need access rights, please contact the maintainer `Stephan Krusche <https://github.com/krusche>`__.

Maintenance
-----------
The Artemis Dockerfile as well as the MySQL image are already maintained because they are used in
other Artemis Docker setups.
Therefore, only Playwright and the Playwright Docker image require active maintenance.
Since the Playwright test suite simulates a real user, it makes sense to execute the test suite with
the latest browser versions.
The Playwright Docker image we use always has browsers with specific versions installed.
Therefore, the
`docker-compose file <https://github.com/ls1intum/Artemis/blob/develop/docker/playwright.yml>`__
should be updated every month to make sure that the latest Playwright image is used.
