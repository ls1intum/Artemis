.. _exercises:

Orion
=====

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

The `Orion plugin <https://github.com/ls1intum/Orion>`_ for `IntelliJ <https://www.jetbrains.com/idea/>`_ supports students, teaching assistants (tutors), and instructors with the conduction of programming exercises. It integrates Artemis in IntelliJ and automates the download and upload of exercises.

Installation
^^^^^^^^^^^^

Orion is installed like every other plugin via IntelliJ's marketplace. Alternatively, the latest build can be found in `Orion's GitHub repository <https://github.com/ls1intum/Orion>`_ as described there.

General Usage
^^^^^^^^^^^^^

After installation, Orion provides a custom tool window at *View -> Tool Windows -> Artemis*. This window contains an integrated browser displaying Artemis.

At the top of the tool window is a back button |back-button|. Clicking it will return to the initial page, that is:

- The exercise details page if an exercise is opened as a student
- The exercise edit in editor page if an exercise is opened as an instructor
- The assessment dashboard if an exercise is opened as a tutor, or, if a submission is downloaded, the assessment details page of that submission
- The Artemis home page if no Artemis project is opened

Settings
^^^^^^^^

Orion's settings are at *Settings -> Tools -> Orion*. The settings include:

- The Artemis base url. Can be changed to switch to a specific Artemis instance. Defaults to https://artemis.ase.in.tum.de. **Important:** The url must not end with a /, otherwise it will not work!
- The Artemis exercise paths. Orion will suggest to store newly downloaded exercises at *default path/course/exercise name*, with the default path dependant of the setting.
- The default commit message
- An option to change the user agent if needed as well as a button to restart the integrated browser. If Orion gets stuck restarting the browser might solve the issue without restarting IntelliJ.

Problems
^^^^^^^^

Please report any issues on `GitHub <https://github.com/ls1intum/Orion>`_.

Orion for Students
------------------

Students can participate in programming exercises via Orion by performing the following steps:

 1. Navigate to the exercise using the integrated browser.
 2. After starting the exercise, a button to |open-in-intellij-button| appears.
 3. After cloning, the exercise can be solved in IntelliJ.
 4. Orion provides a button to |submit-button|. This will automatically commit and push the local changes and display the result of the automatic tests in IntelliJ.

Orion for Instructors
---------------------

Instructors can set up programming exercises via Orion by performing the following steps:

 1. The exercise needs to be created as described at the programming exercise creation step 1 and 2.
 2. After the creation, navigate to the instructor exercise overview using the integrated browser
 3. Each programming exercise will provide a button to |edit-in-intellij-button|. The button is rightmost in the table an might require scrolling. This will download the template, solution and test repository of the exercise
 4. Edit the repository files in IntelliJ.
 5. Orion provides a button to |submit-button|. This will commit and push all local changes to their respective repository.
 6. The integrated browser displays the editor to edit the problem statement.
 7. Orion also provides a button to |test-locally-button|, which will copy the local template or solution (whichever was selected) and the tests into a new folder and execute them locally.

Orion for Tutors
----------------

Tutors can assess programming exercises via Orion by performing the following steps:

 1. Navigate to the exercise's assessment dashboard using the integrated browser
 2. There will be a button to |assess-in-orion-button|. It automatically downloads the automatic tests.
 3. After downloading or opening the exercise in IntelliJ, the normal submission overview is shown in the integrated browser. Each submission can be opened in Orion, new submissions can be started by |start-assessment-in-orion-button|. This downloads the submission files and overwrites the previous submission.
 4. The student's code is in the directory *assignment*. The tests are in the directory *tests*. Additionally, there are two directories *template* and *studentSubmission* that contain internal files and should be ignored.
 5. Opening a file from the assignment opens the editor with two available modes, which can be switched using the tabs at the bottom of the editor.
- In edit mode ("Text"), the files can be edited regularly, e.g. to try out fixes
- In assessment mode ("Assessment"), the files are read-only, but assessment comments can be added, similar to assessment in Artemis. Click the plus on the gutter on the left of the editor to add a new comment.
 6. The integrated browser displays the problem statement, the assessment instructions, and the buttons to edit the general feedback.

.. |back-button| image:: ../orion/back-button.png
.. |submit-button| image:: ../orion/submit-button.png
.. |test-locally-button| image:: ../orion/test-locally-button.png
.. |open-in-intellij-button| image:: ../orion/open-in-intellij-button.png
.. |edit-in-intellij-button| image:: ../orion/edit-in-intellij-button.png
.. |assess-in-orion-button| image:: ../orion/assess-in-orion-button.png
.. |start-assessment-in-orion-button| image:: ../orion/start-assessment-in-orion-button.png
