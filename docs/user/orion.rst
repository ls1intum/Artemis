.. _orion:

Orion
=====

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

The `Orion plugin <https://github.com/ls1intum/Orion>`_ for `IntelliJ <https://www.jetbrains.com/idea/>`_ supports students, teaching assistants (tutors), and instructors with the conduction of programming exercises. It integrates Artemis in IntelliJ and automates the download (clone) and upload (push) of exercises.

Installation
^^^^^^^^^^^^

Orion is installed like every other plugin via IntelliJ's marketplace. Alternatively, although usually not needed, it is possible to install builds directly from `Orion's GitHub repository <https://github.com/ls1intum/Orion>`_. The installation process is described in the `readme <https://github.com/ls1intum/Orion#testing-of-pull-requests>`_.

General Usage
^^^^^^^^^^^^^

After installation, Orion provides a custom tool window at *View -> Tool Windows -> Artemis*. This window contains an integrated browser displaying Artemis.

At the top of the tool window are the following buttons:

- A help button |help-button|, which opens this documentation page.
- A back button |back-button|. Clicking it returns to the initially opened page, that is:

   - The exercise details page if an exercise is opened as a student.
   - The exercise edit in editor page if an exercise is opened as an instructor.
   - The assessment dashboard if an exercise is opened as a tutor, or, if a submission is downloaded, the assessment details page of that submission.
   - The Artemis home page if no Artemis project is opened.

  If you opened this page in Orion, use the back button to return to Artemis.

Settings
^^^^^^^^

Orion's settings are at *Settings -> Tools -> Orion*. The settings include:

- Artemis base url: Can be changed to switch to a specific Artemis instance. Defaults to https://artemis.ase.in.tum.de. **Important:** The url must not end with a ``/``, otherwise it does not work!
- Artemis exercise paths: Orion suggests to store newly downloaded exercises at ``default-path/course/exercise-name``, with the default path dependent of the setting.
- Default commit message: The default message for each commit.
- Change user agent: The user agent is sent to Artemis to identify Orion. Usually, no changes are required.
- Restart the integrated browser: If Orion gets stuck, restarting the browser might solve the issue without restarting IntelliJ.

Problems
^^^^^^^^

Please report any issues on `Orion's GitHub repository <https://github.com/ls1intum/Orion>`_.

Participation in Orion
----------------------

Students can participate in programming exercises via Orion by performing the following steps:

 1. Navigate to the exercise using the integrated browser.
 2. After starting the exercise, click |open-in-intellij-button|.
 3. After cloning, the exercise can be solved in IntelliJ.
 4. To submit the changes, click |submit-button|. This automatically commits and pushes the local changes and displays the result of the automatic tests in IntelliJ.

A Gif showcasing the usage for students:

  .. image:: https://raw.githubusercontent.com/ls1intum/Orion/master/.github/media/orion_workflow.gif
            :align: center
            :width: 100%

Exercise Creation in Orion
--------------------------

Instructors can set up programming exercises via Orion by performing the following steps:

 1. The exercise needs to be created as described at the :ref:`exercise creation <programming_exercise_creation>` of programming exercises, step 1 and 2.
 2. After the creation, navigate to the instructor exercise overview using the integrated browser.
 3. Each programming exercise provides a button to edit the exercise in Orion |edit-in-intellij-button|. The button is rightmost in the table and might require scrolling. Clicking it downloads the template, solution and test repository of the exercise.
 4. Edit the repository files in IntelliJ.
 5. To submit the changes, click |submit-button|. This commits and pushes all local changes to their respective repository.
 6. The integrated browser displays the editor to update the problem statement.
 7. To test the code locally, click |test-locally-button|, which copies the tests with the local template or solution (whichever was selected) into a new folder and executes them locally.

Assessment in Orion
-------------------

Tutors can assess programming exercises via Orion by performing the following steps:

 1. Navigate to the assessment dashboard of the exercise using the integrated browser.
 2. Click |assess-in-orion-button| to automatically set up the assessment project.
 3. After downloading or opening the project in IntelliJ, the submission overview is shown in the integrated browser. Each submission can be opened in Orion. To start a new submission, click |start-assessment-in-orion-button|. This downloads the submission files and overwrites the previous submission.
 4. The student's code is located in the directories ``assignment`` and ``studentSubmission`` (``assignment`` contains the files that can be edited, ``studentSubmission`` contains an uneditable copy that can be assessed). The tests are in the directory ``tests``.
 5. Opening a file in either ``assignment`` or ``studentSubmission`` opens the editor with two available modes that can be switched using the tabs at the bottom of the editor.

   - In edit mode ("Text" tab), the files can be edited regularly, e.g. to try out fixes.
   - In assessment mode ("Assessment" tab), the student's submission without the local changes is displayed in read-only mode. In this mode, assessment comments can be added, similar to the assessment in Artemis. Click the plus on the gutter on the left of the editor to add a new comment.

 6. The integrated browser displays the problem statement, the assessment instructions, and the buttons to edit the general feedback.

.. |back-button| image:: orion/back-button.png
.. |help-button| image:: orion/help-button.png
.. |submit-button| image:: orion/submit-button.png
.. |test-locally-button| image:: orion/test-locally-button.png
.. |open-in-intellij-button| image:: orion/open-in-intellij-button.png
.. |edit-in-intellij-button| image:: orion/edit-in-intellij-button.png
.. |assess-in-orion-button| image:: orion/assess-in-orion-button.png
.. |start-assessment-in-orion-button| image:: orion/start-assessment-in-orion-button.png
