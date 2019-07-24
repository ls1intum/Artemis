## Setup Guide for Artemis

In this guide you will learn how to setup the development environment for your contribution on Artemis.

### Server Setup

To start the Artemis application server from the development environment, first import the project into IntelliJ and then make sure to install the Spring Boot plugins to run the main class de.tum.in.www1.artemis.ArtemisApp. Before the application runs, you have to configure the file `application-artemis.yml` in the folder `src/main/resources/config`. 

```
artemis:
  repo-clone-path: ./repos/
  encryption-password: <encrypt-password>
  jira:
    url: https://jirabruegge.in.tum.de
    user: <username>
    password: <password>
    admin-group-name: tumuser
  version-control:
    url: https://repobruegge.in.tum.de
    user: <username>
    secret: <password>
  bamboo:
    url: https://bamboobruegge.in.tum.de
    bitbucket-application-link-id: de1bf2e0-eb40-3a2d-9494-93cbe2e22d08
    user: <username>
    password: <password>
    empty-commit-necessary: true
    authentication-token: <token>
  lti:
    id: artemis_lti
    oauth-key: artemis_lti_key
    oauth-secret: <secret>
    user-prefix_edx: edx_
    user-prefix_u4i: u4i_
    user-group-name_edx: edx
    user-group-name_u4i: u4i
  git:
    name: artemis
    email: artemis@in.tum.de
```
Change all entries with ```<...>``` with proper values, e.g. your TUM Online account credentials to connect to the given instances of JIRA, Bitbucket and Bamboo. Alternatively, you can connect to your local JIRA, Bitbucket and Bamboo instances.
Be careful that you don't commit changes in this file. Best practice is to specify that your local git repository ignores this file or assumes that this file is unchanged. 

The Artemis server should startup by running the main class ```de.tum.in.www1.artemis.ArtemisApp``` using Spring Boot.

One typical problem in the development setup is that an exception occurs during the database initialization. Artemis uses [Liquibase](https://www.liquibase.org) to automatically upgrade the database scheme after changes to the data model. This ensures that the changes can also be applied to the production server. In some development environments, it can be the case that the liquibase migration from an empty database scheme to the current version of the database scheme fails, e.g. due to the fact that the asynchronous migration is too slow. In these cases, it can help to manually import an existing database scheme using e.g. MySQL Workbench or Sequel Pro into the `Artemis` database scheme in your MySQL server. You can find a recent scheme in the `data` folder in this git repository. If you then start the application server, liquibase will recognize that all migration steps have already been executed. In case you encounter errors with liquibase checksum values, run the following command in your terminal / command line:

```
java -jar liquibase-core-3.5.3.jar --url=jdbc:mysql://localhost:3306/ArTEMiS --username=root --password='' --classpath=mysql-connector-java-5.1.43.jar  clearCheckSums
```
You can download the required jar files here:

* [liquibase-core-3.5.3.jar](http://central.maven.org/maven2/org/liquibase/liquibase-core/3.5.3/liquibase-core-3.5.3.jar)
* [mysql-connector-java-5.1.43.jar](http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.43/mysql-connector-java-5.1.43.jar)

As an alternative you can use this gradle command:

```
./gradlew liquibaseClearChecksums
```

If you use a password, you need to adapt it in Artemis/gradle/liquibase.gradle

**Please note:** Artemis uses Spring profiles to segregate parts of the application configuration and make it only available in certain environments. For development purposes, the following program arguments can be used to enable the `dev` profile and the profiles for JIRA, Bitbucket and Bamboo:

    --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis

### Client Setup

After installing Node, you should be able to run the following command to install development tools. You will only need to run this command when dependencies change in [package.json](package.json).

```
yarn install
```

To start the client application in the browser, use the following command:

```
yarn start
```

This compiles TypeScript code to JavaScript code, starts the hot module replacement feature in Webpack (i.e. whenever you change a TypeScript file and save, the client is automatically reloaded with the new code) and will start the client application in your browser on `http://localhost:9000`. If you have activated the JIRA profile (see above in Server Setup) and if you have configured `application-artemis.yml` correctly, then you should be able to login with your TUM Online account.

For more information, review [Working with Angular](https://www.jhipster.tech/development/#working-with-angular). For further instructions on how to develop with JHipster, have a look at [Using JHipster in development](http://www.jhipster.tech/development).

### Using docker-compose

A full functioning development environment can also be set up using docker-compose: 

1. Install [docker](https://docs.docker.com/install/) and [docker-compose](https://docs.docker.com/compose/install/)
2. Configure the credentials in `application-artemis.yml` in the folder `src/main/resources/config` as described above
3. Run `docker-compose up`
4. Go to [http://localhost:9000](http://localhost:9000)

The client and the server will run in different containers. As yarn is used with its live reload mode to build and run the client, any change in the client's codebase will trigger a rebuild automatically. In case of changes in the codebase of the server one has to restart the `artemis-server` container via `docker-compose restart artemis-server`.

(Native) Running and Debugging from IDEs is currently not supported.

**Get a shell into the containers:**

* app container: `docker exec -it $(docker-compose ps -q artemis-app) sh`
* mysql container: `docker exec -it $(docker-compose ps -q artemis-mysql) mysql`

**Other useful commands:**

* Stop the server: `docker-compose stop artemis-server` (restart via `docker-compose start artemis-server`)
* Stop the client: `docker-compose stop artemis-client` (restart via `docker-compose start artemis-client`)
