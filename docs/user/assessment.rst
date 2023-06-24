.. _assessment:

Assessment
==========

.. contents:: Content of this document
    :local:
    :depth: 2


Overview
--------

Artemis offers three different modes of exercise assessment:

- **Manual:** Reviewers must manually grade the submissions of students.
- **Automatic:** Artemis automatically grades the submissions of students.
- **Semi-Automatic:** Artemis provides an automatic starting point for the reviewers to manually improve the grading afterward.

Manual Assessment
-----------------

Manual assessment refers to evaluating or grading student assignments, typically performed by a reviewer rather than in an automated way. It is available and tailored to all exercise types except quiz exercises.

Manual assessment in Artemis involves the following steps:

1. Submission: Students submit their assignments through Artemis.
2. Review: Reviewers access the submitted work and put a **lock** on the submission, preventing inconsistency and ambiguity the other reviewer evaluations can cause. Then they review it carefully, considering the objectives, requirements, and criteria established for the assessment.
3. Evaluation: Based on their assessment, reviewers assign scores, provide feedback, or grade the student's work, keeping the grading criteria in mind to ensure consistency and fairness. In the end, reviewers submit or cancel their evaluation, which revokes the lock for use by the other reviewers.
4. Student feedback: Students rate the quality of the feedback to motivate the reviewers to provide high-quality feedback to improve understanding and prevent misconceptions.


Assessment Dashboard
^^^^^^^^^^^^^^^^^^^^

To keep track of manual assessments, Artemis offers an assessment dashboard.
It represents the assessment progress of each exercise by showing the state of the exercise, the total number of submissions, the number of submissions that have been assessed, and the number of complaints and feedback requests.
It also shows the average rating the students have given each exercise.

    .. figure:: assessment/assessment-dashboard.png
            :alt: A picture of the assessment dashboard (of an exam)
            :align: center

Each exercise also has its own assessment dashboard that shows all of this information for a single exercise.

Structured Grading Criteria
^^^^^^^^^^^^^^^^^^^^^^^^^^^

To ensure consistency, fairness, and transparency, as well as to simplify the grading process, Artemis provides structured grading instructions (comparable to grading rubrics) that can be dragged and dropped, making it easier and faster to provide feedback. They include predefined feedback and points so that different reviewers can follow the same criteria when assessing student work. Additionally, they provide transparency to students, allowing them to understand how reviewers evaluate their submissions.

    .. figure:: assessment/grading-criteria.png
            :alt: A picture of the assessment user interface with grading criteria, student submission, and example solution
            :align: center


Integrated Training Process
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Instructors can use the assessment training process to make the grading more consistent.
An integrated training process for reviewers based on example submissions and example assessments ensures that reviewers have enough knowledge to assess submissions and provide feedback properly.
They define a series of example submissions and assessments that the reviewers must first read through.

The integrated training process is as follows:

+-----------------------------------------------------------------------+----------------------------------------------------------------------+
|| The instructor creates an example submission for the exercise and    | .. figure:: assessment/instructor-example-submission.png             |
|| select the desired assessment mode that can define how a reviewer    |    :alt: Example Submission                                          |
|| has to confirm that the example was understood.                      |                                                                      |
|| Depending on the assessment mode, the reviewer can either read and   |                                                                      |
|| confirm (Read and Confirm) or must assess the example submission     |                                                                      |
|| correctly (Assess Correctly).                                        |                                                                      |
+-----------------------------------------------------------------------+----------------------------------------------------------------------+
|| The instructor adds an example assessment to the aforementioned      | .. figure:: assessment/instructor-example-assessment.png             |
|| exercise submission. It is also possible to add actual student       |    :alt: Example Assessment                                          |
|| submissions and “import” them as example submissions to make the     |                                                                      |
|| training more realistic and to reduce the effort of coming up with   |                                                                      |
|| new example submissions.                                             |                                                                      |
+-----------------------------------------------------------------------+----------------------------------------------------------------------+
|| The reviewer sees the status of an exercise during the whole         | .. figure:: assessment/reviewer-exercise-status.png                  |
|| process.                                                             |    :alt: Exercise status                                             |
+-----------------------------------------------------------------------+----------------------------------------------------------------------+
|| The reviewer reads the grading instructions (problem statement,      | .. figure:: assessment/reviewer-assessment-instructions.png          |
|| grading criteria and example solution) and confirms that he/she      |    :alt: Assessment Instructions                                     |
|| has understood it.                                                   |                                                                      |
+-----------------------------------------------------------------------+----------------------------------------------------------------------+
|| As soon as the reviewer starts participating in the exercise, he/she | .. figure:: assessment/reviewer-read-confirm.png                     |
|| can start reading example submissions and assessments provided       |    :alt: Reviewer reads and confirms example submission              |
|| by the instructor if the assessment mode is "Read and Confirm".      |                                                                      |
||                                                                      |                                                                      |
|| On the other hand, if the assessment mode is "Assess Correctly",     | .. figure:: assessment/reviewer-assess-correctly.png                 |
|| Artemis compares the reviewer's assessment with the one provided     |    :alt: Reviewer assess example incorrectly                         |
|| by the instructor. If it does not match, it gives feedback on        |                                                                      |
|| why the assessment should be different.                              |                                                                      |
+-----------------------------------------------------------------------+----------------------------------------------------------------------+

Double-blind Grading
^^^^^^^^^^^^^^^^^^^^

The manual assessment begins after the due date for an exercise has passed for all students and is **double-blind**.
It means that the reviewers do not know the names of the students they assess, and the students do not know the identity of the reviewers.
The double-blind grading aims to minimize bias and increase the objectivity of the assessment.
It implies that both the students and the reviewers are **blind** to each other's identities, ensuring that their expectations or biases do not influence the results.


.. _exercise_complaints:

Complaints
^^^^^^^^^^

After receiving a grade, students can complain about an exercise assessment if the instructor enabled this option, the complaint due date is still ongoing, and the students think the evaluation needs to be revised.
The instructor can set a maximum number of allowed complaints per course. These so-called tokens are used for each complaint. The token is returned to the student if the reviewer accepts the complaint.
It means a student can submit as many complaints as they want, as long as they are accepted.

The complaint process is as follows:

1. The student opens the related exercise, interacts with the "Complain" button below the exercise instructions, and writes additional text before submitting a complaint to justify the reevaluation.

   .. figure:: assessment/complaint-submission.png
            :alt: Complaint submission
            :align: center

2. The reviewer interacts with the "Assessment Dashboard" button of the desired course, which displays the table for all the course exercises.

    +---------------------------------------------------------+--------------------------------------------------+
    | .. figure:: assessment/assessment-dashboard-button.png  | .. figure:: assessment/assessment-dashboard.png  |
    |    :alt: Assessment Dashboard button                    |    :alt: Assessment Dashboard                    |
    +---------------------------------------------------------+--------------------------------------------------+


3. By interacting with the respective "Exercise Dashboard" button, the reviewer opens the exercise-specific dashboard and assesses students' submissions.
   Upon evaluation, the reviewer puts a lock expiring automatically in 24 hours in addition to an option of unlocking manually.

    .. figure:: assessment/exercise-dashboard.png
            :alt: Exercise Dashboard
            :align: center

4. The reviewer decides on the student's complaint for each submission.

    +------------------------------------+---------------------------------------------+
    || In case of a justification, the   | .. figure:: assessment/accept-complaint.png |
    || reviewer adds feedback blocks and |    :alt: Accept Complaint                   |
    || interacts with the "Accept        |                                             |
    || complaint" button. Feedback points|                                             |
    || can be both negative and positive.|                                             |
    +------------------------------------+---------------------------------------------+
    || Otherwise, the reviewer explains  | .. figure:: assessment/reject-complaint.png |
    || why the complaint was rejected    |    :alt: Reject Complaint                   |
    || and interacts with the "Reject    |                                             |
    || complaint" button.                |                                             |
    +------------------------------------+---------------------------------------------+
    || If the reviewer cannot decide     | .. figure:: assessment/lock.png             |
    || between accepting and rejecting,  |    :alt: Complaint Lock                     |
    || it is possible to remove the lock |                                             |
    || so that another reviewer can      |                                             |
    || evaluate the complaint.           |                                             |
    +------------------------------------+---------------------------------------------+

5. Student can rate the quality of the feedback.

    .. figure:: assessment/student-feedback.png
            :alt: Student Feedback
            :align: center

.. _exercise_more_feedback_request:

More Feedback Requests
^^^^^^^^^^^^^^^^^^^^^^

Another possibility after receiving an assessment is the *More Feedback Request*.
Unlike complaints, they do not cost a token, but the reviewer cannot change the score after a feedback request.

    .. figure:: assessment/more-feedback.png
            :alt: Exercise Dashboard
            :align: center

For the reviewers, the process is identical to the complaint process.

.. warning::
    Sending a *More Feedback Request* removes the option to complain about the assessment entirely.
    The score cannot be changed even if the reviewer made a mistake during the first assessment and acknowledges this during the *More Feedback Request*.


Grading Leaderboard
^^^^^^^^^^^^^^^^^^^

Artemis also offers a way for instructors to monitor the reviewers' assessments based on the students' feedback on reviewer evaluation. The first part of this is the grading leaderboard, which is visible to all reviewers.

    .. figure:: assessment/leaderboard.png
            :alt: Grading leaderboard
            :align: center

The leaderboard shows the number of assessments each reviewer has done and the number of feedback requests and accepted complaints about them.
It also shows the average score the reviewer has given and the average rating they received for their assessments. It helps to track and display the performance and rankings of the reviewers who assess and provide feedback on student submissions.
Additionally, Artemis automatically checks for “Issues with reviewer performance” in case reviewers significantly deviate from the average.

Automatic Assessment
--------------------

Automatic assessment is available for programming and quiz exercises.
For quiz exercises, this is the only mode of assessment available. Artemis automatically grades students' submissions after the quiz due date has passed. See the section about :ref:`quiz` for more information about this.

For programming exercises, this is done via instructor-written test cases that are run for each submission either during or after the due date. See the section about :ref:`programming` for detailed information about this.
Instructors can enable complaints for automatically graded programming exercises.
