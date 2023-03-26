.. tutorialgroups:

Tutorial Groups
===============

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------
Artemis facilitates the coordination of tutorial groups in a course. Tutorial groups are a learning strategy where students teach and learn from each other in small groups (20-30). In this strategy, proficient students act as tutors and lead the groups. The tutor and the group members usually meet weekly either on campus or online. Students present their solutions to homework assignments or other tasks and receive feedback and suggestions from the tutor and their peers.

Setting up Tutorial Group Plan as an Instructor
-----------------------------------------------

Tutorial groups can be managed by instructors by navigating to the course's  ``Tutorial Groups`` page.

Initial Configuration
^^^^^^^^^^^^^^^^^^^^^

|instructors-button|

Before the tutorial group feature can be used, three configurations need to be set up:

* **Time zone information:** This ensures that the tutorial group meeting times are displayed correctly for each student and instructor.

* **Default tutorial group period:** This is the semester period when the groups usually meet. It is used to prefill the meeting period when creating a new tutorial group. The tutorial group period can be changed later on for each group individually.

* **Artemis managed tutorial group channels:** This option allows Artemis to create and manage a dedicated channel for each tutorial group in the 'Messages' section of the course. This feature is only selectable if the course has the ``Messaging`` feature enabled in the course settings. If activated, tutorial group channels can still be managed manually but Artemis automatically performs some common tasks, such as:

  * Adding and removing students from the channel when they register or unregister for the tutorial group

  * Making the assigned tutor a moderator of the channel

  * Deleting the channel when the tutorial group is deleted

The three settings can be changed later in the ``Global Configuration`` section of the tutorial group page.

|instructors-checklist|


Creating Tutorial Groups
^^^^^^^^^^^^^^^^^^^^^^^^

Tutorial groups can be created manually or by importing a CSV file. Importing a CSV file is a convenient option if the tutorial groups and student assignments already exist in a campus management system (e.g. TUM-Online). This way, both the groups and the assignments can be created at once.


|instructors-create-groups|


The assigned tutor and the session schedule are the most important settings of a tutorial group. The tutor holds the sessions, tracks the number of attending students, and gives feedback to the students. The tutor can also register or unregister students and edit the sessions by cancelling or rescheduling them. The meeting schedule shows the regular times of the sessions during the semester. It is used to create the individual sessions automatically.


|instructors-tutorial-group-form|
|instructors-csv-import|


Managing assigned Tutorial Groups as a Tutor
--------------------------------------------
..ToDo

Viewing Tutorial Groups as a Student
------------------------------------
..ToDo

.. |instructors-button| image:: tutorialgroups/instructors-tutorial-group-button.png
    :width: 1000
.. |instructors-checklist| image:: tutorialgroups/instructors-checklist.png
    :width: 1000
.. |instructors-create-groups| image:: tutorialgroups/instructors-create-groups.png
    :width: 1000
.. |instructors-tutorial-group-form| image:: tutorialgroups/instructors-tutorial-group-form.png
    :width: 500
.. |instructors-csv-import| image:: tutorialgroups/instructors-csv-import.png
    :width: 500