If you are a student, and want to know how to use the Artemis Version Control for checking out repositories locally, :ref:`checkout this guide.<local-vc>`

The following tables lists the different types of repositories and gives an overview of the access rights different users have.
To gain these access rights, a user must assume the indicated role in the course the repository belongs to.

The different repository types are:

:Base: This includes all repositories that are set up when the exercise is created (template repository, solution repository, tests repository, auxiliary repositories).
:Student Assignment: A student's assignment repository copied from the template repository. This includes team assignment repositories.
:Teaching Assistant (TA) Assignment: An assignment repository created by a teaching assistant for themself.
:Instructor Assignment: An assignment repository created by an editor or instructor for themselves. Not available for exam exercises.
:Student Practice: A student's practice repository copied either from the template repository, or from their assignment repository. Can only be created after the due date of the exercise has passed. Not available for exam exercises.
:Teaching Assistant (TA) Practice: A practice repository created by a teaching assistant for themself. Not available for exam exercises.
:Instructor Practice: A practice repository created by an editor or instructor for themselves. Not available for exam exercises.
:Instructor Exam Test Run: A test run repository created by an editor or instructor for an exam. An instructor can create an exam test run before the start date of the exam to allow the instructor to test the exam from a student perspective before releasing it. This repository should be deleted before the exam is conducted.


The different roles are:

:Student (S): A student in the course.
:Teaching Assistant (TA): A teaching assistant (tutor) in the course.
:Editor: An editor in the course.
:Instructor (I): An instructor in the course.

.. note:: Editors and instructors are included in the role "Instructor" in the table as both roles have the same access rights.

The different points in time are:

:Before start: Before the exercise start date for a course exercise, before the exam start date for an exam exercise.
:Working time: After the exercise start date for a course exercise, after the exam release date for an exam exercise, before the due date for a course exercise, before the exam end date for an exam exercise.
:After due: After the due date for a course exercise, after the exam end date for an exam exercise.

.. note:: For the Instructor Exam Test Run Repository, the point in time "Before start" is the start date of the test run, and the point in time "After due" is the end date of the test run. Both are before the exam start date.

Read access **(R)** includes ``git fetch``, ``git clone``, and ``git pull``, if you are using your local Git client to access the repository.
Write access **(W)** corresponds to ``git push`` if you are using your local Git client.

+--------------------------+------+---------------+--------------+
| Repository type          | Role | Point in time | Access       |
+==========================+======+===============+==============+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
| Base                     | TA   | all           | R            |
|                          +------+---------------+--------------+
|                          | I    | all           | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | Before start  | none         |
|                          +------+---------------+--------------+
|                          | S    | Working time  | R/W          |
|                          +------+---------------+--------------+
| Student Assignment       | S    | After due     | R :sup:`1`   |
|                          +------+---------------+--------------+
|                          | TA   | all           | R            |
|                          +------+---------------+--------------+
|                          | I    | all           | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
|                          | TA   | Before start  | R            |
|                          +------+---------------+--------------+
| TA Assignment            | TA   | Working time  | R/W          |
|                          +------+---------------+--------------+
|                          | TA   | After due     | R            |
|                          +------+---------------+--------------+
|                          | I    | all           | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
| Instructor Assignment    | TA   | all           | R            |
|                          +------+---------------+--------------+
|                          | I    | all           | R/W :sup:`2` |
+--------------------------+------+---------------+--------------+
|                          | S    | Before start  | none         |
|                          +------+---------------+--------------+
|                          | S    | Working time  | none         |
|                          +------+---------------+--------------+
|                          | S    | After due     | R/W          |
|                          +------+---------------+--------------+
|                          | TA   | Before start  | none         |
|                          +------+---------------+--------------+
| Student Practice         | TA   | Working time  | none         |
|                          +------+---------------+--------------+
|                          | TA   | After due     | R            |
|                          +------+---------------+--------------+
|                          | I    | Before start  | none         |
|                          +------+---------------+--------------+
|                          | I    | Working time  | none         |
|                          +------+---------------+--------------+
|                          | I    | After due     | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
|                          | TA   | Before start  | none         |
|                          +------+---------------+--------------+
|                          | TA   | Working time  | none         |
|                          +------+---------------+--------------+
| TA Practice              | TA   | After due     | R/W          |
|                          +------+---------------+--------------+
|                          | I    | Before start  | none         |
|                          +------+---------------+--------------+
|                          | I    | Working time  | none         |
|                          +------+---------------+--------------+
|                          | I    | After due     | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
|                          | TA   | Before start  | none         |
|                          +------+---------------+--------------+
|                          | TA   | Working time  | none         |
|                          +------+---------------+--------------+
| Instructor Practice      | TA   | After due     | R            |
|                          +------+---------------+--------------+
|                          | I    | Before start  | none         |
|                          +------+---------------+--------------+
|                          | I    | Working time  | none         |
|                          +------+---------------+--------------+
|                          | I    | After due     | R/W          |
+--------------------------+------+---------------+--------------+
|                          | S    | all           | none         |
|                          +------+---------------+--------------+
| Instructor Exam Test Run | TA   | all           | R            |
|                          +------+---------------+--------------+
|                          | I    | all           | R/W          |
+--------------------------+------+---------------+--------------+

1) Only valid for course exercises.
Students cannot read their repository for exam exercises after the due date.

2) The instructor can access the Instructor Assignment repository using the online editor either from the *Edit in editor* view accessed via the Course Management (-> Exercises -> Edit in editor)  or from the Course Overview (clicking on the course card -> Open code editor).
After the due date of the exercise has passed, the instructor can push to the repository only via the online editor reached from the Course Management or using their local Git client.
The online editor accessible from the Course Overview will show that the repository is locked, as it does for all students taking part in the course.

.. note:: The Practice repositories as well as the TA assignment repository and the instructor assignment repository in the table above only exist for course exercises.

