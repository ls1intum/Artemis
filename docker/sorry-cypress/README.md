# Sorry Cypress

Sorry Cypress is an open-source, self-hosted alternative to the Cypress Dashboard, which allows you to manage, execute, and observe your Cypress test runs and results.

## Usage

The dashboard is available on https://sorry-cypress.ase.cit.tum.de, which is secured with a basic authentication. The credentials can be found on  [Confluence](https://confluence.ase.in.tum.de/display/ArTEMiS/Sorry+Cypress+Dashboard).

After login, you can see the active projects for artemis. One for the MySQL and one for the Postgres runs. Clicking on either project will reveal the last runs. Each run is named with a combination of the branch name and the run number. E.g for the 4th run of the branch `feature/add-awesomeness` the name would be `feature/add-awesomeness #4`. On the run overview page, each run shows some basic information, like the run time, if the run is currently running (red dot in front of the run name), the passed and failed tests etc. 
To debug one of the recent runs, click on the single run and sorry cypress now shows a table with all the different test files that were tested. Select a failed test and you can now see a video of this run. Since cypress is a graphical testing tool, analyzing a video is ofter much more helpful then analyzing the corresponding logs. 

*Hint*: Sometimes it takes a while until the videos are available. So if no video is shown for the run, just visit the page again some minutes later.

## Tech Stack

Also visit the sorry cypress docs for more information: https://docs.sorry-cypress.dev/

### Dashboard 

As mentioned earlier the dashboard allows the developers to get an overview of the last test runs and helps them debug the runs.

### API

The API service is used to gather information from the CI and also provide them to the dashboard. 

### MongoDB

This service is used as a database to store all the run information.

### Director

A service that accepts data from the api and saves it into the database.

### Minio

A AWS S3 and Google Cloud compatible dropin replacement, that is used as a storage provider, storing the videos and screenshots of the run. 

### Nginx

Used as a reverse proxy, that allows to access the different endpoints via HTTPS.
