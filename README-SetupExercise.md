# ArTEMiS Application Exercise Setup Instructions

Please follow these instructions carefully to set up a new exercise for the students in course. The instructions demonstrate setting up a Java exercise, however this can be adapted to any programming language supported by the build server.

## Exercise Preparation
**Requirements:** [Apache Maven](https://maven.apache.org/), [Git](https://git-scm.com/)
1. Create a new Maven project using the CLI (insert your own group ID and exercise name):

        mvn -B archetype:generate \
            -DarchetypeGroupId=org.apache.maven.archetypes \
            -DgroupId=de.tum.cs.i1.pse2016 \
            -DartifactId=exercisename
                           
2. Configure the Maven project:
    * Add your dependencies to `pom.xml`
    * Set compiler level to Java 8 in `pom.xml`.
3. Add your code:
    - Add the assignment code to `src/main/java/`.
    - Add the testing code to `src/test/java/`.
    - Add testing resources to `src/test/resources`.
4. Initialize the Git repositories:
    - `git init` in exercise root folder.
    - Add `src/test/*` to the repository’s `.gitignore`.
    - `git init` in `src/test`.
    - Add and commit files.


## Bitbucket Setup

You will need to create a unique Bitbucket project per exercise.

1. Choose an appropriate project name and key. E.g. for the exercise "*State Chart*" in the course "*Introduction to Software Engineering (Summer 2016)*" a suitable project key would be `EIST16SC`.
2. Give the user *exerciseapp* admin permissions on the project.
3. Inside the project, create two repositories:
    1. Repository containing the exercise code for the students. Since the repository contains no reference to the Bitbucket project outside of the Bitbucket UI, we recommend to name this repository with the project key. Add this repository as a remote for your local exercise repository and push.
    2. Repository containing the testing code. Choose a descriptive name, e.g. "*TEST*". Add this repository as a remote for your local exercise’s test repository and push.

## Bamboo Setup

1. Create a new plan:
    1. Choose to create it inside a new project. Preferably you should use the same project key as in Bitbucket.
    2. Give the plan a descriptive key such as "*BASE*". The plan name and description are not important.
    3. Choose an arbitrary linked repository (Will be deleted later on since we do not use linked repositories).
    4. Click "*Configure plan*".
    5. Click  "*Create*" (Setup will be completed in next step).
2. Configure the plan (`Open plan` -> `Actions` -> `Configure plan`):
    * Permissions: Remove the *View* permission for logged in and anonymous users. Add admin permissions for the user *exerciseapp*.
    * Repositories: 
        1. Remove the repository which was linked during the initial setup.
        2. Click "*Add repository*".
        3. Add the exercise code repository. **Important:** Give it the name “*Assignment*”.
        4. Add the test repository. Here, an arbitrary name can be chosen.
    * Stages:
        1. Choose the default job in the default stage.
        2. Edit the source code checkout task: Add the exercise code repository (make sure it points to the repository and **not** to the default repository) and the test repository (set the checkout directory so it is checked out into `src/test/`).
        3. Add a Maven 3.x task with goal `clean test`. Check "*This build will produce test results*". Other settings might depend on your specific code setup.
        4. Add a script task with the following (inline) content:

                curl -k -X POST https://exercisebruegge.in.tum.de/api/results/${bamboo.planKey}
        5. *Optional*: Add a requirement "*AgentType equals Amazon*" to the job if it should only be built on remote build agents.

## [ArTEMiS Application Setup](https://exercisebruegge.in.tum.de)

Make sure you are in user group *ls1instructor* to have admin permissions in ArTEMiS.

1. If you have not created a course yet, do so now (`Entities` -> `Course` -> `Create a new Course`). The student group name defines in which group (in JIRA) users need to be to see this course. 

2. Create a new exercise (`Entities` -> `Course` -> `Create a new Exercise`) with the following parameters:
    * *Title*: A descriptive title for the exercise
    * *Base Repository URL*: The URL of the repository containing the exercise code (e.g. *https://repobruegge.in.tum.de/scm/EIST16SC/EIST16SC.git*)
    * *Base Build Plan ID*: The identifier of the base build plan in the format `PROJECTKEY-PLANKEY` (e.g. *EIST16SC-BASE*)
    * *Publish Build Plan URL*: When set to true, students will see a link to their personalized build plan (e.g. for teaching release management)
    * *Release Date*: When the exercise will be visible to students (**Not respected yet**)
    * *Due Date*: (**Not implemented yet**)
    * *Course*: The course with which this exercise should be associated

## Known Limitations
* **Usernames**: Plans can only be cloned for users without underscores (*_*) in their usernames. This is due to restrictions on Bamboo's plan keys.
* **Test results**: Bamboo's REST API only allows access to details on failed tests via each job inside a plan. We currently only retrieve those details for the default job.
