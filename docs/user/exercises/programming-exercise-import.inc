On exercise import, Artemis copies the repositories, build plans, interactive problem statement, and grading configuration from the imported exercise.

1. **Open Course Management**

- Open |course-management|
- Navigate into **Exercises** of your preferred course

    .. figure:: general/course-management-course-dashboard-exercises.png
              :align: center

2. **Import programming exercise**

- Click on **Import Programming Exercise**

    .. figure:: programming/course-management-exercise-dashboard-import.png
              :align: center

- Select an exercise to import

    .. figure:: programming/course-management-exercise-dashboard-import-modal.png
              :align: center

.. note::
  Instructors can import exercises from courses in which they are registered as instructors.

- Artemis provides special options to update the assessment process

    .. figure:: programming/programming-import-options.png
              :align: center

    - **Recreate Build Plans**: Create new build plans instead of copying them from the imported exercise
    - **Update Template**: Update the template files in the repositories. This can be useful if the imported exercise is old and contains outdated dependencies.
      For Java, Artemis replaces JUnit4 with Ares (which includes JUnit5) and updates the dependencies and plugins with the versions found in the latest template.
      Afterwards, you might need to adapt the test cases.

- Instructors are able to activate/deactivate static code analysis. Changing this option from the original value requires the activation of **Recreate Build Plans** and **Update Template**.

.. note::
  **Recreate Build Plans** and **Update Template** are automatically set if the static code analysis option changes compared to the imported exercise.
  The plugins, dependencies, and static code analysis tool configurations are added/deleted/copied depending on the new and the original state of this option.

- Fill out all mandatory values and click on |import|

.. note::
  The interactive problem statement can be edited after finishing the import. Some options such as **Sequential Test Runs** cannot be changed on exercise import.

.. |import| image:: programming/programming-exercise-import-button.png
