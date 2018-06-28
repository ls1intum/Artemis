# ArTEMiS: AuTomated assEssment Management System 
This application was generated using JHipster 4.14.4, you can find documentation and help at [http://www.jhipster.tech/documentation-archive/v4.14.4](http://www.jhipster.tech/documentation-archive/v4.14.4).

**Curent version:** 2.0.1

## Main features
ArTEMiS has the following main features:
1. **Programming exercises** with version control and automatic assessment with test cases and continuous integration
2. **Quiz exercises** with multiple choice questions and drag and drop questions (beta)
3. **Modeling exercises** with semi-automatic assessment using machine learning concepts (coming soon)

All these exercises are supposed to be run either live in-class with instant feedback or as homework. Students can submit their solutions multiple times within the due date and use the (semi-)automatically provided feedback to improve their solution.

## Top-Level Design

The following UML diagram shows the top-level design of ArTEMiS which is decomposed into an application client and an application server. The application server then connects to a version control system (VCS), a continuous integration system (CIS) and a user management system (UMS).

![Top-Level Design](doc/TopLevelDesign.png "Top-Level Design")

While ArTEMiS includes generic adapters to these three external systems with a defined protocol which can be instantiated to connect to any VCS, CIS or UMS, it also provides 3 concrete implementations for these adapters to connect to:

1. **VCS:** Atlassian Bitbucket Server
2. **CIS:** Atlassian Bamboo Server
3. **UMS:** Atlassian JIRA Server (more specifically Atlassian Crowd on the JIRA Server)

## Exercise Workflow

Conducting a programming exercise consists of 7 steps distributed among instructor, ArTEMiS and students:

1. **Instructor prepares exercise:** Set up a repository containing the exercise code and test cases, build instructions on the CI server, and configures the exercise in ArTEMiS.
2. **Student starts exercise:** Click on start exercise on ArTEMiS which automatically generates a copy of the repository with the exercise code and configures a build plan accordingly.
3. **Optional: Student clones repository:** Clone the personalized repository from the remote VCS to the local machine.
4. **Student solves exercise:** Solve the exercise with an IDE of choice on the local computer or in the online editor.
5. **Student uploads solution:** Upload changes of the source code to the VCS by committing and pushing them to the remote server (or by clicking submit in the online editor).
6. **CI server verifies solution:** verify the student's submission by executing the test cases (see step 1) and provide feedback which parts are correct or wrong.
7. **Student reviews personal result:** Reviews build result and feedback using ArTEMiS. In case of a failed build, reattempt to solve the exercise (step 4).
8. **Instructor reviews course results:** Review overall results of all students, and react to common errors and problems.

The following activity diagram shows this exercise workflow. 

![Exercise Workflow](doc/ExerciseWorkflow.png "Exercise Workflow")

## Online Editor

The following screenshot shows the online editor with interactive and dynamic exercise instructions on the right side.
Tasks and UML diagram elements are referenced by test cases and update their color from red to green after students submit a new version and all test cases associated with a task or diagram element pass.
This allows the students to immediately recognize which tasks are already fulfilled and is particularly helpful for programming beginners.

![Online Editor](doc/OnlineEditor.png "Online Editor****")

## Development Setup

This project uses Spring Boot with Java 8 on the application server and Angular 5 with TypeScript on the client.

Before you can build this project, you must install and configure the following dependencies on your machine:

1. [Node.js](https://nodejs.org): We use Node to run a development web server and build the project.
Depending on your system, you can install Node either from source or as a pre-packaged bundle.
2. [Yarn](https://yarnpkg.com): We use Yarn to manage Node dependencies.
Depending on your system, you can install Yarn either from source or as a pre-packaged bundle.

After installing Node, you should be able to run the following command to install development tools.
You will only need to run this command when dependencies change in [package.json](package.json).

```
yarn install
```

To compile TypeScript code to JavaScript code and start the hot module replacement feature in Webpack, use the following command, see [Working with Angular]()https://www.jhipster.tech/development/#working-with-angular)

```
yarn start
```

[Bower](https://bower.io) is used to manage CSS and JavaScript dependencies used in this application (in the hybrid mode with Angular 1.5.8). You can upgrade dependencies by
specifying a newer version in [bower.json](bower.json). You can also run `bower update` and `bower install` to manage dependencies.
Add the `-h` flag on any command to see how you can use it. For example, `bower update -h`.

For further instructions on how to develop with JHipster, have a look at [Using JHipster in development](http://www.jhipster.tech/development).

ArTEMis is based on [JHipster](https://jhipster.github.io), i.e. Java [Spring Boot](http://projects.spring.io/spring-boot) development on the application server and Javascript ([Angular 5](https://angular.io)) development on the application client in the browser. To get an overview of the used technology, have a look at [https://jhipster.github.io/tech-stack](https://jhipster.github.io/tech-stack) and other tutorials on the JHipster homepage.  

You can find tutorials how to setup JHipster in an IDE ([IntelliJ](https://www.jetbrains.com/idea) is recommended, but it also runs in other IDEs as well) on [https://jhipster.github.io/configuring-ide](https://jhipster.github.io/configuring-ide).

To start ArTEMiS from the development environment, first import the project and then make sure to install the Spring Boot plugins to run the main class de.tum.in.www1.artemis.ArTEMiSApp. Before the application runs, you have to configure the file application-dev.yml in the folder src/main/resources/config/ and add the following details:

```
artemis:
  repo-clone-path: ./repos/
  encryption-password: <password>
  result-retrieval-delay: 5000
  jira:
    url: https://jirabruegge.in.tum.de
    user: <user>
    password: <password>
    admin-group-name: <admin-group>
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
    name: ArTEMiS
    email: <email>
```
Change the entries with ```<...>``` with proper values, e.g. your TUM Online account to connect to the given instances of JIRA, Bitbucket and Bamboo. Alternatively, you can connect to your local JIRA, Bitbucket and Bamboo instances (see Docker setup below). 

In addition, you have to install MySQL, setup a root user without password. The required ArTEMiS scheme will be created / updated automatically at startup of the server application.

The ArTEMiS server should startup by running the main class ```de.tum.in.www1.artemis.ArTEMiSApp``` using Spring Boot.

To access the ArTEMiS client in your browser, you have to install node, npm and yarn (see above) and execute the following commands in the terminal / command line in the ArTEMiS root folder:

```
yarn install
bower install
yarn start
```

Please note, that `bower install` is only necessary for the hybrid mode (with Angular 1 --> ng1) and will hopefully be removed soon.
`yarn install` is only necessary when dependencies to node modules change. `yarn start` compiles the TypeScript client code and starts the client with automatic code replacement.
This means, whenever you change a TypeScript file and save, the client is automatically reloaded with the new code. 
After that you should be able to access http://127.0.0.1:9000/ and login with your TUM Online account (if you use our JIRA instance).

    
## Profiles

ArTEMiS uses Spring profiles to segregate parts of the application configuration and make it only available in certain environments. For development purposes, the following program arguments can be used to enable the `dev` profile and the profiles for JIRA, Bitbucket and Bamboo:

    --spring.profiles.active=dev,bamboo,bitbucket,jira 

## Building for production

To optimize the ArTEMiS application for production, run:

```
./gradlew -Pprod clean bootRepackage
```

This will concatenate and minify the client CSS and TypeScript / JavaScript files. It will also modify `index.html` so it references these new files.
To ensure everything worked, run:

```
java -jar build/libs/*.war
```

Then navigate to [http://localhost:8080](http://localhost:8080) in your browser.

Refer to [Using JHipster in production](http://www.jhipster.tech/production) for more details.

## Deployment

The following UML deployment diagram shows a typical deployment of ArTEMiS application server and application client. Student, Instructor and Teaching Assistant (TA) computers are all equipped equally with the ArTEMiS application client being displayed in the browser.

The Continuous Integration Server typically delegates the build jobs to local build agents within the university infrastructur or to remote build agents, e.g. hosted in the Amazon Cloud (AWS).

![Deployment Overview](doc/DeploymentOverview.png "Deployment Overview")


## Data Model

The ArTEMiS application server used the following (initial) data model in the MySQL database. It supports multiple courses with multiple exercises. Each student in the participating student group can participate in the exercise by clicking the **Start Exercise** button. Then a repository and a build plan for the student (User) will be created and configured. The initialization state variable (Enum) helps to track the progress of this complex operation and allows to recover from errors. A student can submit multiple solutions by committing and pushing the source code changes to a given example code into the version control system. Each submission is automatically tested by the continuous integration server, which notifies the ArTEMiS application server, when a new result exists. In addition, teaching assistants can assess student solutions and "manually" create results.
The current data model is more complex and supports different types of exercises such as programming exercises, modeling exercises and quiz exercises.

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
