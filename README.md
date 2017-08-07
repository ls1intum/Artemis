# ArTEMiS: AuTomated assEssment Management System 

The following UML diagram shows the top-level design of ArTEMiS which is decomposed into an application client and an application server. The application server then connects to a version control system (VCS), a continuous integration system (CIS) and a user management system (UMS).

![Top-Level Design](doc/TopLevelDesign.png "Top-Level Design")

While ArTEMiS includes generic adapters to these three external systems with a defined protocol which can be instantiated to connect to any VCS, CIS or UMS, it also provides 3 concrete implementations for these adapters to connect to:

1. **VCS:** Atlassian Bitbucket Server
2. **CIS:** Atlassian Bamboo Server
3. **UMS:** Atlassian JIRA Server (more specifically Atlassian Crowd on the JIRA Server)

## Development Setup
ArTEMis is based on [JHipster](https://jhipster.github.io), i.e. Java [Spring Boot](http://projects.spring.io/spring-boot) development on the application server and Javascript ([Angular 1](https://angularjs.org)) development on the application client in the browser. To get an overview of the used technology, have a look at [https://jhipster.github.io/tech-stack](https://jhipster.github.io/tech-stack) and other tutorials on the JHipster homepage.  

You can find tutorials how to setup JHipster in an IDE ([IntelliJ](https://www.jetbrains.com/idea) is recommended, but it also runs in other IDEs as well) on [https://jhipster.github.io/configuring-ide](https://jhipster.github.io/configuring-ide).

To start ArTEMiS from the development environment, first import the project and then make sure to install the Spring Boot plugins to run the main class de.tum.in.www1.exerciseapp.ExerciseApplicationApp. Before the application runs, you have to configure the file application-dev.yml in the folder src/main/resources/config/ and add the following details:

```
exerciseapp:
  repo-clone-path: ./repos/
  encryption-password: <password>
  result-retrieval-delay: 5000
  jira:
    url: https://jirabruegge.in.tum.de
    user: <user>
    password: <password>
    instructor-group-name: <admin-group>
  bitbucket:
    url: https://repobruegge.in.tum.de
    user: <user>
    password: <password>
  bamboo:
    url: https://bamboobruegge.in.tum.de
    bitbucket-application-link-id: de1bf2e0-eb40-3a2d-9494-93cbe2e22d08
    user: <user>
    password: <password>
  lti:
    id: exerciseapp_lti
    oauth-key: exerciseapp_lti_key
    oauth-secret: <secret>
    user-prefix: edx_
    user-group-name: edx
  git:
    name: ExerciseApplication
    email: <email>
```
Change the entries with <...> with proper values, e.g. your TUM Online account to connect to the given instances of JIRA, Bitbucket and Bamboo. Alternatively, you can conncet to your local JIRA, Bitbucket and Bamboo instances (see Docker Setup below). 

In addition, you have to install MySQL, setup a root user without password and create an ExerciseApplication scheme.

Then ArTEMiS should startup by running the main class ```de.tum.in.www1.exerciseapp.ExerciseApplicationApp``` using Spring Boot.

To access ArTEMiS in your browser, you have to install npm and execute the following commands in the terminal / command line in the ArTEMiS root folder:

```
npm install
bower install
gulp
```

After that you should be able to access http://127.0.0.1:8080/ and login with your TUM Online account (if you use our JIRA instance).

## Docker Setup
If you want to connect to your own JIRA, Bitbucket and Bamboo instances, you can use Docker. Docker can be used to setup development containers on your own computer for the required external components MySQL, Bitbucket (version control), Bamboo (continuous integration) and JIRA (user management).

1. Install Docker and `docker-compose`
2. Run `docker-compose -f src/main/docker/dev.yml up`. 
3. This will startup the following containers. When accessing for the first time you need to setup a license and an admin user. 
    1. Bitbucket: [http://127.0.0.1:7990](http://127.0.0.1:7990)
    2. Bamboo: [http://127.0.0.1:8085](http://127.0.0.1:8085)
    3. JIRA: [http://127.0.0.1:8000](http://127.0.0.1:8000)
    4. MySQL: 127.0.0.1:3306 (user `root` without password)
4. In Bamboo go to `Administration` -> `Application Links` and add Bitbucket using the URL `http://exerciseapplication-bitbucket:7990`. Use OAuth without Impersonation.
5. In Bitbucket go to `Administration` -> `Application Links` and add Bamboo using the URL `http://exerciseapplication-bamboo:8085`. Use OAuth without Impersonation.
6. Configure JIRA/Bitbucket/Bamboo with Groups, Test Users, Plans etc. Please note that as default JIRA/Bitbucket/Bamboo do have seperate user databases and test users might need to be created on all instances.
7. Change `application-dev.yml` to add the following elements:
    
        exerciseapp:
          repo-clone-path: ./repos/
          result-retrieval-delay: 5000
          encryption-password: X7RNnJUzeoUpB2EQsK
          jira:
            url: http://localhost:8000
            instructor-group-name: jira-administrators
          bitbucket:
            url: http://localhost:7990
            user: bitbucket
            password: bitbucket
          bamboo:
            url: http://localhost:8085
            bitbucket-application-link-id: 0c3af16d-2aef-3660-8dd8-4f87042833de
            user: bamboo
            password: bamboo
          lti:
            oauth-key: exerciseapp_lti_key
            oauth-secret: 7pipQv9MeidmZvMsTL
            create-user-prefix: edx_

    
## Profiles

ArTEMiS uses Spring profiles to segregate parts of the application configuration and make it only available in certain environments. For development purposes, the following program arguments can be used to enable the `dev` profile and the profiles for JIRA, Bitbucket and Bamboo:

    --spring.profiles.active=dev,bamboo,bitbucket,jira 

## Deployment

The following UML deployment diagram shows a typical deployment of ArTEMiS application server and application client. Student, Instructor and Teaching Assistant (TA) computers are all equipped equally with the ArTEMiS application client being displayed in the browser.

The Continuous Integration Server typically delegates the build jobs to local build agents within the university infrastructur or to remote build agents, e.g. hosted in the Amazon Cloud (AWS).

![Deployment Overview](doc/DeploymentOverview.png "Deployment Overview")


## Data Model

The ArTEMiS application server uses the following data model in the MySQL database. It supports multiple courses with multiple exercises. Each student in the participating student group can participate in the exercise by clicking the **Start Exercise** button. Then a repository and a build plan for the student (User) will be created and configured. The initialization state variable (Enum) helps to track the progress of this complex operation and allows to recover from errors. A student can submit multiple solutions by committing and pushing the source code changes to a given example code into the version control system. Each submission is automatically tested by the continuous integration server, which notifies the ArTEMiS application server, when a new result exists. In addition, teaching assistants can assess student solutions and "manaully" create results.

![Data Model](doc/DataModel.png "Data Model")

In the future, we want to allow different types of exercises, so expect multiple subclasses for programming, modeling and quiz exercises.


## Server Architecture

The following UML component diagram shows more details of the ArTEMiS application server architecture and its REST interfaces to the application client.

![Server Architecture](doc/ServerArchitecture.png "Server Architecture")

## Adapters

The following UML component diagram shows the details of the Version Control Adapter that allows to connect to multiple Version Control Systems. The other adapters for Continuous Integration and User Management have a similar structure

![Version Control Adapter](doc/VersionControlAdapter.png "Version Control Adapter")

The **Version Control Adapter** includes the following abstract interface that concrete connectors have to implement:

```
+ copyRepository(baseRepository, user)
+ configureRepository(repository, user)
+ deleteRepository(repository)
+ getRepositoryWebUrl(repository)
```

The **Continuous Integration Adapter** includes the following abstract interface that concrete connectors have to implement:

```
+ copyBuildPlan(baseBuildPlan, user)
+ configureBuildPlan(buildPlan, repository, user)
+ deleteBuildPlan(buildPlan)
+ onBuildCompleted(buildPlan)
+ getBuildStatus(buildPlan)
+ getBuildDetails(buildPlan)
+ getBuildPlanWebUrl(buildPlan)
```
