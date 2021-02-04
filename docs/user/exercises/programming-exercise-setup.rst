Features
^^^^^^^^
Artemis and its version control and continuous integration infrastructure is independent of the programming language and thus supports
teaching and learning with any programming language that can be compiled and tested on the command line.
Instructors have a lot of freedom in defining the environment (e.g. using build agents and Docker images) in which student code is executed and tested.
To simplify the setup of programming exercises, Artemis supports several ``templates`` that show how the setup works.
Instructors can still use those templates to generate programming exercises and then adapt and customize the settings in the repositories and build plans.


- The support for a specific programming language ``templates`` depends on the used ``continuous integration`` system. The table below gives an overview:

  +----------------------+--------+---------+
  | Programming Language | Bamboo | Jenkins |
  +======================+========+=========+
  | Java                 | yes   | yes      |
  +----------------------+--------+---------+
  | Python               | yes   | yes      |
  +----------------------+--------+---------+
  | C                    | yes   | yes      |
  +----------------------+--------+---------+
  | Haskell              | yes   | yes      |
  +----------------------+--------+---------+
  | Kotlin               | yes   | no       |
  +----------------------+--------+---------+
  | VHDL                 | yes   | no       |
  +----------------------+--------+---------+
  | Assembler            | yes   | no       |
  +----------------------+--------+---------+
  | Swift                | yes   | no       |
  +----------------------+--------+---------+

- Not all ``templates`` support the same feature set.
  Depending on the feature set, some options might not be available during the creation of the programming exercise.
  The table below provides an overview of the supported features:

  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Programming Language | Sequential Test Runs | Static Code Analysis | Plagiarism Check | Package Name | Solution Repository Checkout |
  +======================+======================+======================+==================+==============+==============================+
  | Java                 | yes                  | yes                  | yes              | yes          | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Python               | yes                  | no                   | yes              | no           | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | C                    | no                   | no                   | yes              | no           | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Haskell              | yes                  | no                   | no               | no           | yes                          |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Kotlin               | yes                  | no                   | no               | yes          | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | VHDL                 | no                   | no                   | no               | no           | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Assembler            | no                   | no                   | no               | no           | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
  | Swift                | no                   | no                   | no               | no           | no                           |
  +----------------------+----------------------+----------------------+------------------+--------------+------------------------------+

  - *Sequential Test Runs*: ``Artemis`` can generate a build plan which first executes structural and then behavioral tests. This feature can help students to better concentrate on the immediate challenge at hand.
  - *Static Code Analysis*: ``Artemis`` can generate a build plan which additionally executes static code analysis tools.
    ``Artemis`` categorizes the found issues and provides them as feedback for the students. This feature makes students aware of code quality issues in their submissions.
  - *Plagiarism Checks*: ``Artemis`` is able to automatically calculate the similarity between student submissions. A side-by-side view of similar submissions is available to confirm the plagiarism suspicion.
  - *Package Name*: A package name has to be provided
  - *Solution Repository Checkout*: Instructors are able to compare a student submission against a sample solution in the solution repository

.. note::
  Only some ``templates`` for ``Bamboo`` support ``Sequential Test Runs`` at the moment.

.. note::
  Instructors are still able to extend the generated programming exercises with additional features that are not available in one specific template.

We encourage instructors to contribute improvements to the existing ``templates`` or to provide new templates. Please contact Stephan Krusche and/or create Pull Requests in the Github repository.


Exercise Creation
^^^^^^^^^^^^^^^^^

1. **Open Course Management**

- Open |course-management|
- Navigate into **Exercises** of your preferred course

    .. figure:: programming/course-management-course-dashboard.png
              :align: center

2. **Generate programming exercise**

- Click on **Generate new programming exercise**

    .. figure:: programming/course-management-exercise-dashboard.png
              :align: center

- Fill out all mandatory values and click on |generate|

    .. figure:: programming/create-programming-1.png
              :align: center

    .. figure:: programming/create-programming-2.png
              :align: center

  Result: **Programming Exercise**

    .. figure:: programming/course-dashboard-exercise-programming.png
              :align: center

  Artemis creates the repositories:

  - **Template:** template code, can be empty, all students receive this code at the beginning of the exercises
  - **Test:** contains all test cases, e.g. based on JUnit and optionally static code analysis configuration files. The repository is hidden for students
  - **Solution:** solution code, typically hidden for students, can be made available after the exercise

  Artemis creates two build plans

  - **Template:** also called BASE, basic configuration for the test + template repository, used to create student build plans
  - **Solution:** also called SOLUTION, configuration for the test + solution repository, used to manage test cases and to verify the exercise configuration

  .. figure:: programming/programming-view-1.png
            :align: center
  .. figure:: programming/programming-view-2.png
            :align: center
  .. figure:: programming/programming-view-3.png
            :align: center

3. **Update exercise code in repositories**

- **Alternative 1:** Clone the 3 repositories and adapt the code on your local computer in your preferred development environment (e.g. Eclipse).

  - To execute tests, copy the template (or solution) code into a folder **assignment** in the test repository and execute the tests (e.g. using maven clean test)
  - Commit and push your changes |submit|

  - **Notes for Haskell:** In addition to the assignment folder, the executables of the build file expect the solution repository checked out in the **solution** subdirectory of the test folder and also allow for a **template** subdirectory to easily test the template on your local machine.
    You can use the following script to conveniently checkout an exercise and create the right folder structure:

    .. code-block:: bash

      #!/bin/sh
      # Arguments:
      # $1: exercise short name as specified on Artemis
      # $2: (optional) output folder name
      #
      # Note: you might want to adapt the `BASE` variable below according to your needs

      if [ -z "$1" ]; then
        echo "No exercise short name supplied."
        exit 1
      fi

      EXERCISE="$1"

      if [ -z "$2" ]; then
        # use the exercise name if no output folder name is specified
        NAME="$1"
      else
        NAME="$2"
      fi

      # default base URL to repositories; change this according to your needs
      BASE="ssh://git@bitbucket.ase.in.tum.de:7999/$EXERCISE/$EXERCISE"

      # clone the test repository
      git clone "$BASE-tests.git" "$NAME" && \
        # clone the template repository
        git clone "$BASE-exercise.git" "$NAME/template" && \
        # clone the solution repository
        git clone "$BASE-solution.git" "$NAME/solution" && \
        # create an assignment folder from the template repository
        cp -R "$NAME/template" "$NAME/assignment" && \
        # remove the .git folder from the assignment folder
        rm -r "$NAME/assignment/.git/"

- **Alternative 2:** Open |edit-in-editor| in Artemis (in the browser) and adapt the code in online code editor

  - You can change between the different repos and submit the code when needed

- **Alternative 3:** Use IntelliJ with the Orion plugin and change the code directly in IntelliJ

  **Edit in Editor**

  .. figure:: programming/instructor-editor.png
            :align: center

- Check the results of the template and the solution build plan
- They should not have the status |build_failed|
- In case of a |build_failed| result, some configuration is wrong, please check the build errors on the corresponding build plan.
- **Hints:** Test cases should only reference code, that is available in the template repository. In case this is **not** possible, please try out the option **Sequential Test Runs**

4. **Optional:** Adapt the build plans

- The build plans are preconfigured and typically do not need to be adapted
- However, if you have additional build steps or different configurations, you can adapt the BASE and SOLUTION build plan as needed
- When students start the programming exercise, the current version of the BASE build plan will be copied. All changes in the configuration will be considered

5. **Optional:** Configure static code analysis tools

- The **Test** repository contains files for the configuration of static code analysis tools, if static code analysis was activated during the creation/import of the exercise
- The folder *staticCodeAnalysisTools* contains configuration files for each used static code analysis tool
- On exercise creation, Artemis generates a default configuration for each tool, which contain a predefined set of parameterized activated/excluded rules. The configuration files serve as a documented template that instructors can freely tailor to their needs.
- On exercise import, Artemis copies the configuration files from the imported exercise
- The following table depicts the supported static code analysis tools for each programming language, the dependency mechanism used to execute the tools and the name of their respective configuration files

+----------------------+-------------------------+-------------------------------+------------------------------+
| Programming Language | Execution Mechanism     | Supported Tools               | Configuration File           |
+----------------------+-------------------------+-------------------------------+------------------------------+
| Java                 | Maven plugins (pom.xml) | Spotbugs                      | spotbugs-exclusions.xml      |
|                      |                         +-------------------------------+------------------------------+
|                      |                         | Checkstyle                    | checkstyle-configuration.xml |
|                      |                         +-------------------------------+------------------------------+
|                      |                         | PMD                           | pmd-configuration.xml        |
|                      |                         +-------------------------------+------------------------------+
|                      |                         | PMD Copy/Paste Detector (CPD) | -                            |
+----------------------+-------------------------+-------------------------------+------------------------------+

.. note::
  The Maven plugins for the Java static code analysis tools provide additional configuration options.

- The build plans use a special task/script for the execution of the tools

.. note::
  Instructors are able to completely disable the usage of a specific static code analysis tool by removing the plugin/dependency from the execution mechanism.
  In case of Maven plugins, instructors can remove the unwanted tools from the *pom.xml*.
  Alternatively, instructors can alter the task/script that executes the tools in the build plan
  PMD and PMD CPD are a special case. Both tools share a common plugin. To disable one or the other, instructors must alter the build plan

6. Adapt the interactive problem statement

  .. figure:: programming/course-dashboard-programming-edit.png
            :align: center

- Click the |edit| button of the programming exercise or navigate into |edit-in-editor| and adapt the interactive problem statement.
- The initial example shows how to integrate tasks, link tests and integrate interactive UML diagrams

7. Configure Grading

- **Test Case Tab**: Adapt the contribution of each test case to the overall score

  .. figure:: programming/configure-grading.png
            :align: center

  .. note::
  Artemis registers the test cases defined in the **Test** repository using the results generated by **Solution** build plan.
  The test cases are only shown after the first execution of the **Solution** build plan

- **Cody Analysis Tab**: Configure the visibility and grading of issues belonging to categories

8. Verify the exercise configuration

- Open the |view| page of the programming exercise

    .. figure:: programming/solution-template-result.png
              :align: center

- The template result should have a score of **0%** with **0 of X passed** or **0 of X passed, 0 issues** (if static code analysis is enabled)
- The solution result should have a score of **100%** with **X of X passed** or **X of X passed, 0 issues** (if static code analysis is enabled)

.. note::
  If static code analysis is enabled and issues are found in the template/solution result, instructors should improve the template/solution or disable the rule, which produced the unwanted/unimportant issue

- Click on |edit|

  - Below the problem statement, you should see **Test cases** ok and **Hints** ok

  .. figure:: programming/programming-edit-status.png
            :align: center


.. |build_failed| image:: ../exams/student/buttons/build_failed.png
.. |edit| image:: programming/edit.png
.. |view| image:: programming/view.png
.. |edit-in-editor| image:: programming/edit-in-editor.png
.. |submit| image:: programming/submit.png
.. |course-management| image:: programming/course-management.png
.. |generate| image:: programming/generate-button.png
