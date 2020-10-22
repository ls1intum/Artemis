Features
^^^^^^^^

- The support for a specific programming language depends on the used ``Continuous Integration`` system. The table below gives an overview:

.. table:: ``Continuous Integration`` system support of programming languages

+----------------------+--------+---------+
| Programming Language | Bamboo | Jenkins |
+======================+========+=========+
| Java                 | true   | true    |
+----------------------+--------+---------+
| Python               | true   | true    |
+----------------------+--------+---------+
| C                    | true   | true    |
+----------------------+--------+---------+
| Haskell              | true   | true    |
+----------------------+--------+---------+
| Kotlin               | true   | false   |
+----------------------+--------+---------+
| VHDL                 | true   | false   |
+----------------------+--------+---------+
| Assembler            | true   | false   |
+----------------------+--------+---------+

- Not all programming languages support the same feature set.
  Depending on the feature set, some options might not be available during the creation of the programming exercise.
  The table below provides an overview of the supported features.

.. table:: Programming language feature sets

+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| Programming Language | Sequential Test Runs | Static Code Analysis | Plagiarism Check | Package Name | Solution Repository Checkout |
+======================+======================+======================+==================+==============+==============================+
| Java                 | true                 | true                 | true             | true         | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| Python               | true                 | false                | true             | false        | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| C                    | false                | false                | true             | false        | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| Haskell              | true                 | false                | false            | false        | true                         |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| Kotlin               | true                 | false                | false            | true         | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| VHDL                 | false                | false                | false            | false        | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+
| Assembler            | false                | false                | false            | false        | false                        |
+----------------------+----------------------+----------------------+------------------+--------------+------------------------------+

  - *Sequential Test Runs*: ``Artemis`` can generate a build plan which first executes structural and then behavioral tests. This feature can help students to better concentrate on the immediate challenge at hand.
  - *Static Code Analysis*: ``Artemis`` can generate a build plan which additionally executes static code analysis tools.
    ``Artemis`` categorizes the found issues and provides them as feedback for the students. This feature makes students aware of code quality issues in their submissions.
  - *Plagiarism Checks*: ``Artemis`` is able to automatically calculate the similarity between student submissions. A side-by-side view of similar submissions is available to confirm the plagiarism suspicion.
  - *Package Name*: A package name has to be provided
  - *Solution Repository Checkout*: Instructors are able to compare a students submissions against a sample solution

.. note::
  Only ``Bamboo`` supports ``Sequential Test Runs`` at the moment.

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
  - Commit and push your changes |submit|

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

5. Adapt the interactive problem statement

  .. figure:: programming/course-dashboard-programming-edit.png
            :align: center

- Click the |edit| button of the programming exercise or navigate into |edit-in-editor| and adapt the interactive problem statement.
- The initial example shows how to integrate tasks, link tests and integrate interactive UML diagrams

6. Configure Grading

  .. figure:: programming/configure-grading.png
            :align: center

7. Verify the exercise configuration

- Open the |view| page of the programming exercise

    .. figure:: programming/solution-template-result.png
              :align: center

- The template result should have a score of **0%** with **0 of X passed**
- The solution result should have a score of **100%** with **X of X passed**

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
