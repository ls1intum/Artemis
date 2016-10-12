# ArTEMiS
AuTomated assEssment Management System

## Exercise Setup
Please follow these instructions carefully to set up a new exercise for the students in course. The instructions demonstrate setting up a Java exercise, however this can be adapted to any programming language supported by the build server.

### [Bitbucket Setup](https://repobruegge.in.tum.de)

You will need to create a unique Bitbucket project per exercise.

1. Choose an appropriate project name and key. E.g. for the exercise "*State Chart*" in the course "*Introduction to Software Engineering (Summer 2016)*" a suitable project key would be `EIST16SC`.
2. Give the user *exerciseapp* admin permissions on the project.
3. Inside the project, create two repositories:
    1. Repository containing the exercise code for the students. Since the repository contains no reference to the Bitbucket project outside of the Bitbucket UI, we recommend to name this repository with the project key. Use the following structure:

            ExerciseStateChart/
                |_ src/
                    |_ ... (source code in standard Java package structure)
                |_ .classpath
                |_ .project
            .gitignore

    2. Repository containing the testing code. Choose a descriptive name, e.g. "*TEST*". Use the following structure:

            test/
                |_ src/
                    |_ ... (test code in standard Java package structure)
            testResources/
                |_ ... (any resources required for tests, e.g. structure definitions)
            pom.xml

4. Add project-wide admin permissions for the user *exerciseapp*.

### [Bamboo Setup](https://bamboobruegge.in.tum.de)

1. Create a new plan:
    1. Choose to create it inside a new project make sure the project key is identical (**!!!**) to the Bitbucket project. 
    2. Give the plan a descriptive key such as "*BASE*". The plan name and description are not important.
    3. Choose an arbitray linked repository (Will be deleted later on since we do not use linked repositories).
    4. Click "*Configure plan*".
    5. Click  "*Create*" (Setup will be completed in next step).
2. Configure the plan (`Open plan` -> `Actions` -> `Configure plan`):
    1. Permissions: Remove the *View* permission for logged in and anonymous users. Add admin permissions for the user *exerciseapp*.
    2. Repositories: 
        1. Remove the repository which was linked during the initial setup.
        2. Click "*Add repository*".
        3. Add the exercise code repository. **Important:** Give it the same name as the actual repository! (In our example: *EIST16SC*).
        4. Add the test repository. Here, an arbitrary name can be chosen.
    3. Stages:
        1. Choose the default job in the default stage.
        2. Edit the source code checkout task: Add the exercise code repository (make sure it points to the repository and **not** to the default repository) and the test repository (set the checkout directory so it is checked out into the exercise code folder).
        3. Add a Maven 3.x task with goal `clean test`. Check "*This build will produce test results*". Other settings might depend on your specific code setup.
        4. Add a script task with the following (inline) content:

                curl -k -X POST https://exercisebruegge.in.tum.de/api/results/${bamboo.planKey}
        5. Add a requirement "*AgentType equals Amazon*" to the job.
        
3. Add project-wide admin permissions for the user *exerciseapp*.

### [Exercise Application Setup](https://exercisebruegge.in.tum.de)

Make sure you are in user group *ls1instructor* to have admin permissions inside the exercise application.

1. If you have not created a course yet, do so now (`Entities` -> `Course` -> `Create a new Course`). The student group name defines in which group users need to be to see this course. 

2. Create a new exercise (`Entities` -> `Course` -> `Create a new Exercise`) with the following parameters:
    * Title: A descriptive title for the exercise
    * Base Project Key: The project key you chose for the Bitbucket and Bamboo projects
    * Base Repository Slug: The slug (available from repository URL) of the exercise code repository
    * Base Build Plan Slug: The slug (available from build plan URL) of the build plan
    * Release Date: When the exercise will be visible to students (**Not respected yet**)
    * Due Date: Cut-off date after which submissions won't be considered for bonus (**Not implemented yet**)
    * Course: The course with which this exercise should be associated

### Limitations
* **Usernames**: Plans can only be cloned for users without underscores (*_*) in their usernames. This is due to restrictions on Bamboo's plan keys.
* **Test results**: Bamboo's REST API only allows access to details on failed tests via each job inside a plan. We currently only retrieve those details for the default job.
