# ArTEMiS: AuTomated assEssment Management System 

The following UML diagram shows the top-level design of ArTEMiS which is decomposed into an application client and an application server. The application server then connects to a version control system (VCS), a continuous integration system (CIS) and a user management system (UMS).

![Top-Level Design](doc/TopLevelDesign.png "Top-Level Design")

While ArTEMiS includes generic adapters to these three external systems with a defined protocol which can be instantiated to connect to any VCS, CIS or UMS, it also provides 3 concrete implementations for these adapters to connect to:

1. **VCS:** Atlassian Bitbucket Server
2. **CIS:** Atlassian Bamboo Server
3. **UMS:** Atlassian JIRA Server (more specifically Atlassian Crowd on the JIRA Server)

## Development Setup
Docker can be used to setup development containers for MySQL, Bitbucket, Bamboo and JIRA.

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
7. Setup your `application.yml` in the root directory of the app, example:
    
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