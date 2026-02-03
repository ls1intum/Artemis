# Easy Artemis set up and running playwright locally

Running playwright locally involves three steps:
1. Run an Artemis application instance, with client and server.
2. If no users have been set up, set up users.
3. Install and run playwright.

## 1. Start Artemis

To start Artemis, depending on your OS, either run `runArtemisInDocker_macOS.sh` or `runArtemisInDocker_linux.sh`.
This will set up the database, start Artemis inside a docker container, and start the client via npm.
After this step, you are be able to access Artemis locally as you usually would be.
Note that you need to run the scripts in step 2 and 3 in another shell, as the client needs to keep running.
In case you stop the client, you can simply re-run it at the root of the Artemis project with `npm run start`.

## 2. Setup users

Playwright needs users for it's tests. If you do not have users set up, you can simply do so by running:

```bash
setupUsers.sh
```

This will create 20 test users.

## 3. Setup Playwright and run Playwright tests

You can run Playwright tests in two different ways: running all tests or running in UI mode.

### Running All Tests
The `startPlaywright.sh` script runs the full suite of Playwright tests in a headless mode, outputting the results to the command line.
- Executes all test cases defined in the Playwright test suite.
- Runs in a headless environment for faster execution.
- Outputs test results, including logs, in the terminal.

### Running Tests in UI mode
The `startPlaywrightUI.sh` script starts Playwright in a graphical mode for debugging and interactive test execution.
- Launches a browser window to display available test cases.
- Allows manual selection and execution of individual or multiple tests.
- Provides real-time debugging features, such as visual test steps and screenshots. 
- Useful for debugging and inspecting browser behavior during test execution.
