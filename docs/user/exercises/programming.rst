Programming Exercise
====================

Conducting a programming exercise consists of 7 steps distributed among
instructor, Artemis and students:

1. **Instructor prepares exercise:** Set up a repository containing the
   exercise code and test cases, build instructions on the CI server,
   and configures the exercise in Artemis.
2. **Student starts exercise:** Click on start exercise on Artemis which
   automatically generates a copy of the repository with the exercise
   code and configures a build plan accordingly.
3. **Optional: Student clones repository:** Clone the personalized
   repository from the remote VCS to the local machine.
4. **Student solves exercise:** Solve the exercise with an IDE of choice
   on the local computer or in the online editor.
5. **Student uploads solution:** Upload changes of the source code to
   the VCS by committing and pushing them to the remote server (or by
   clicking submit in the online editor).
6. **CI server verifies solution:** verify the student’s submission by
   executing the test cases (see step 1) and provide feedback which
   parts are correct or wrong.
7. **Student reviews personal result:** Reviews build result and
   feedback using Artemis. In case of a failed build, reattempt to solve
   the exercise (step 4).
8. **Instructor reviews course results:** Review overall results of all
   students, and react to common errors and problems.

The following activity diagram shows this exercise workflow.

.. figure:: programming/ExerciseWorkflow.png
   :alt: Exercise Workflow
   :align: center

   Exercise Workflow

Exercise Creation
-----------------

1. **Open Course Management**

- `https://artemis.ase.in.tum.de/#/course-management <https://artemis.ase.in.tum.de/#/course-management>`_
- Navigate into **Exercises** of your preferred course

    .. figure:: programming/course-management-course-dashboard.png
              :align: center

2. **Generate programming exercise**

- Click on **Generate new programming exercise**

    .. figure:: programming/course-management-exercise-dashboard.png
              :align: center

- Fill out all required values and click on **Generate**

    .. figure:: programming/create-programming-1.png
              :align: center

    .. figure:: programming/create-programming-2.png
              :align: center

  Result: **Programming Exercise**

    .. figure:: programming/course-dashboard-exercise-programming.png
              :align: center

  Artemis creates the repositories:

  - **Template:** template code, can be empty, all students receive this code at the beginning of the exercises
  - **Test:** contains all test cases, e.g. based on JUnit, hidden for students
  - **Solution:** solution code, typically hidden for students, can be made available after the exercise

  Artemis creates two build plans

  - **Template:** also called BASE, basic configuration for the test + template repository, used to create student build plans
  - **Solution:** also called SOLUTION, configuration for the test + solution repository, used to manage test cases and to verify the exercise configuration

  .. figure:: programming/programming-view-1.png
            :align: center
  .. figure:: programming/programming-view-2.png
            :align: center
  .. figure:: programming/programming-view-2.png
            :align: center

3. **Update exercise code in repositories**

- **Alternative 1:** Clone the 3 repositories and adapt the code on your local computer in your preferred development environment (e.g. Eclipse)

  - To execute tests, copy the template (or solution) code into a folder **assignment** in the test repository and execute the tests (e.g. using maven clean test)
  - Commit and push your changes

- **Alternative 2:** Open Edit in Editor in Artemis (in the browser) and adapt the code in online code editor

  - You can change between the different repos and submit the code when needed

- **Alternative 3:** Use IntelliJ with the Orion plugin and change the code directly in IntelliJ

  **Edit in Editor**

  .. figure:: programming/instructor-editor.png
            :align: center

- Check the results of the template and the solution build plan
- They should not have the status **build failed**
- In case of a **build failed** result, some configuration is wrong, please check the build errors on the corresponding build plan.
- **Hints:** Test cases should only reference code, that is available in the template repository. In case this is **not** possible, please try out the option **Sequential Test Runs**

4. **Optional:** Adapt the build plans

- The build plans are preconfigured and typically do not need to be adapted
- However, if you have additional build steps or different configurations, you can adapt the BASE and SOLUTION build plan as needed
- When students start the programming exercise, the current version of the BASE build plan will be copied. All changes in the configuration will be considered

5. Adapt the interactive problem statement

  .. figure:: programming/course-dashboard-programming-edit.png
            :align: center

- Click the Edit button of the programming exercise or navigate into Edit in Editor and adapt the interactive problem statement.
- The initial example shows how to integrate tasks, link tests and integrate interactive UML diagrams

6. Configure Grading

  .. figure:: programming/configure-grading.png
            :align: center

7. Verify the exercise configuration

- Open the **View** page of the programming exercise

    .. figure:: programming/solution-template-result.png
              :align: center

- The template result should have a score of **0%** with **0 of X passed**
- The solution result should have a score of **100%** with **X of X passed**

- Click on **Edit**

  - Below the problem statement, you should see **Test cases** ok and **Hints** ok

  .. figure:: programming/programming-edit-status.png
            :align: center

Online Editor
-------------

The following screenshot shows the online code editor with interactive
and dynamic exercise instructions on the right side. Tasks and UML
diagram elements are referenced by test cases and update their color
from red to green after students submit a new version and all test cases
associated with a task or diagram element pass. This allows the students
to immediately recognize which tasks are already fulfilled and is
particularly helpful for programming beginners.

.. figure:: programming/CodeEditor.png
   :alt: Online Editor
   :align: center

   Online Editor

Testing with Artemis Java Test Sandbox
--------------------------------------

Artemis Java Test Sandbox *(abbr. AJTS)* is a JUnit 5 extension for easy and secure Java testing
on Artemis.

Its main features are

* a security manager to prevent students crashing the tests or cheating
* more robust tests and builds due to limits on time, threads and io
* support for public and hidden Artemis tests, where hidden ones obey a custom deadline
* utilities for improved feedback in Artemis like processing multiline error messages
  or pointing to a possible location that caused an Exception
* utilities to test exercises using System.out and System.in comfortably

**For more information see https://github.com/ls1intum/artemis-java-test-sandbox**


Using adapters to support multiple VCS
--------------------------------------

The following UML component diagram shows the details of the Version
Control Adapter that allows to connect to multiple Version Control
Systems. The other adapters for Continuous Integration and User
Management have a similar structure

.. figure:: programming/VersionControlAdapter.png
   :alt: Version Control Adapter
   :align: center

   Version Control Adapter

The **Version Control Adapter** includes abstract interface definitions.
Among others, concrete connectors have to implement the following
methods:

::

   + copyRepository(baseRepository, user)
   + configureRepository(repository, user)
   + deleteRepository(repository)
   + getRepositoryWebUrl(repository)
   + ...

The **Continuous Integration Adapter** includes abstract interface
definitions. Among others, concrete connectors have to implement the
following methods:

::

   + copyBuildPlan(baseBuildPlan, user)
   + configureBuildPlan(buildPlan, repository, user)
   + deleteBuildPlan(buildPlan)
   + onBuildCompleted(buildPlan)
   + getBuildStatus(buildPlan)
   + getBuildDetails(buildPlan)
   + ...
