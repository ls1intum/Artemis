# Easy Artemis set up and running playwright locally

Running playwright locally involves three steps:
1. Run an Artemis application instance, with client and server.
2. If no users have been set up, set up users.
3. Install and run playwright.

Note: run all setup scripts from within this directory. (cd supporting_scripts/playwright)

## 1. Start Artemis

To start Artemis, depending on your OS, either run `runArtemisInDocker_macOS.sh` or `runArtemisInDocker_linux.sh`.
This will set up the database, start Artemis inside a docker container, and start the client via npm.
After this step, you are be able to access Artemis locally as you usually would be.
Note that you need to run the scripts in step 2 and 3 in another shell, as the client needs to keep running.
In case you stop the client, you can simply re-run it at the root of the Artemis project with `npm run start`.

## 2. Setup users

Playwright needs users for it's tests. If you do not have users set up, you can simply do so by running:
`setupUsers.sh`
This will create 20 test users.

## 3. Setup Playwright and run Playwright in UI-mode

Simply run: `startPlaywright.sh`. This will install the necessary dependencies for playwright and start it in UI mode.
If you already have playwright installed, you can also start playwright directly from the `src/test/playwright` directory with `npm run playwright:open`.
