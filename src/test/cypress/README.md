# Notes on using Cypress

## Using cypress
* Start the client and server of Artemis (if you are running the client on a different port than 8080 adjust the baseUrl in the Artemis/cypress.json file)
* Adjust the (admin-) username and password in Artemis/cypress.env.json
* Run `` yarn cypress:open `` to open the cypress dashboard
* In the dashboard you can run every spec file individually by clicking on it. This will open a new browser, which will execute the test
* Alternatively you can run cypress in headless mode by running `` yarn cypress:run ``. This won't open the cypress dashboard, will run all test files in a headless browser and will record the test runs. The recording files can be found under 'src/test/cypress/videos' and 'src/test/cypress/screenshots'
