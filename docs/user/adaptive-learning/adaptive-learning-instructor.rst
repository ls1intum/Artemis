Manage Adaptive Learning (Instructor)
=======================================

.. contents:: Content of this document
    :local:
    :depth: 2

Competencies
------------
A competency is a overarching learning objective that ties together various lectures and exercises. It has a title, description and a `taxonomy <https://en.wikipedia.org/wiki/Bloom%27s_taxonomy>`_.
Students can then view their their progress in a competency (see also :ref:`Competencies for Students <competencies_student>`).

A prerequisite is a competency that students are expected to have already mastered before the course. Instructors select competencies from previous courses they taught as a prerequisite, or create new ones.

Manage Competencies
^^^^^^^^^^^^^^^^^^^^
Instructors can manage competencies and prerequisites of a course in the *Competency Management* view.

|instructors-competency-management|

From this view, they can create view, edit and delete all competencies (and prerequisites) of their course and create new ones.
Additionally, they have multiple options to :ref:`import_competencies` and they can :ref:`generate_competencies` using Iris.
Most of the following actions are also possible for prerequisites (except for some import actions and generating). As the workflows are quite similar they will only be described for competencies.

Create/Edit Competencies
^^^^^^^^^^^^^^^^^^^^^^^^

An instructor can create or edit competencies using the following form.

|instructors-learning-goal-edit|

Besides a title and description, they can optionally set a `taxonomy <https://en.wikipedia.org/wiki/Bloom%27s_taxonomy>`_.
The mastery threshold describes the minimum average score required for a student to reach 100% confidence in this competency.
The current average score of all linked exercises shown on this page can be used as a basis for defining a reasonable threshold value.
Instructors can link competencies to lecture units on this page by first choosing a lecture and then selecting desired lecture units.

Alternatively, instructors can also link competencies to an exercise or lecture unit on the respective management page using the selection box shown below.

|instructors-learning-goals-link|

.. _import_competencies:

Import Competencies
^^^^^^^^^^^^^^^^^^^

Instructors have three ways to import competencies.

*1. Import all Competencies of a Course*
This option opens a modal in which instructors can select one of their previous courses, importing all competencies (and relations) into the current course.
Use this option to directly import the complete competency model of another course.

|import-all|

*2. Import from another Course*

TODO
Use this option if you only want to import some competencies of another course.

|import-course|

*3. Import Standardized Competencies*

TODO
three ways to import -> show button
- import all
- import from other courses
- import from srandardized -> link to admin guide

|import-standardized|

.. _generate_competencies:

Generate Competencies
^^^^^^^^^^^^^^^^^^^^^

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/46941?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Watch this video on TUM-Live.
    </iframe>

TODO

Learning Paths
--------------

Instructors can enable learning paths for their courses either by editing the course or on the dedicated learning path management page. This will generate individualized learning paths for all course participants.

Once the feature is enabled, instructors get access to each student's learning path. Instructors can search for students by login or name and view their respective learning path graph.

|instructors-learning-path-management|

.. |instructors-competency-management| image:: instructors-competency-management.png
    :width: 1000
.. |import-all| image:: import-all.png
    :width: 600
.. |import-course| image:: import-course.png
    :width: 600
.. |import-standardized| image:: import-standardized.png
    :width: 600
.. |instructors-learning-goal-edit| image:: instructors-learning-goal-edit.png
    :width: 1000
.. |instructors-learning-goals-link| image:: instructors-learning-goals-link.png
    :width: 600
.. |instructors-learning-path-management| image:: instructors-learning-path-management.png
    :width: 1000
