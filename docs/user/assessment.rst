.. _assessment:

Assessment
==========

.. contents:: Content of this document
    :local:
    :depth: 2


Overview
--------

Artemis offers three different modes of exercise assessment:

- **Manual:** Tutors must manually grade the submissions of students.
- **Automatic:** Artemis automatically grades the submissions of students.
- **Semi-Automatic:** Artemis provides an automatic starting point for the tutors to manually improve the grading afterward.

Manual Assessment
-----------------

Manual assessment refers to evaluating or grading student assignments, typically performed by a tutor rather than in an automated way. It is available and tailored to all exercise types except quiz exercises.

Manual assessment in Artemis involves the following steps:

1. Submission: Students submit their assignments through Artemis.
2. Review: Reviewers access the submitted work and put a **lock** on the submission, preventing inconsistency and ambiguity the other reviewer evaluations can cause. Then they review it carefully, considering the objectives, requirements, and criteria established for the assessment.
3. Evaluation: Based on their assessment, reviewers assign scores, provide feedback, or grade the student's work keeping the grading criteria in mind to ensure consistency and fairness. In the end, reviewers submit or cancel their evaluation, which revokes the lock for use by the other reviewers.
4. Student feedback: Students rate the quality of the feedback to motivate the reviewers to provide high-quality feedback to improve understanding and prevent misconceptions.
            

Assessment Dashboard
^^^^^^^^^^^^^^^^^^^^

To keep track of the manual assessments, Artemis offers the assessment dashboard.
It represents the assessment progress of each exercise by showing the state of the exercise, the total number of submissions, the number of submissions that have been assessed, and the number of complaints and more feedback requests.
It also shows the average rating the students have given to each exercise.

    .. figure:: assessment/assessment-dashboard.png
            :alt: A picture of the assessment dashboard (of an exam)
            :align: center

Each exercise also has its own assessment dashboard that shows all of this information for a single exercise.

Structured Grading Criteria
^^^^^^^^^^^^^^^^^^^^^^^^^^^

To ensure consistency, fairness, transparency, and to simplify the grading process, Artemis provides structured grading instructions (comparable to grading rubrics) that can be dragged and dropped, making it easier and faster to provide feedback. They include predefined feedback and points so that different reviewers can follow the same criteria when assessing student work. Additionally, they provide transparency to students, allowing them to understand how reviewers evaluate their submissions.

    .. figure:: assessment/grading-criteria.png
            :alt: A picture of the assessment user interface with grading criteria, student submission and example solution
            :align: center


Integrated Training Process
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Instructors can use the assessment training process to make the grading more consistent. An integrated training process for reviewers based on example submissions and example assessments ensures that reviewers have enough knowledge to properly assess submissions and provide feedback. They define a series of example submissions and assessments that the tutors must first read through.

Double-blind Grading
^^^^^^^^^^^^^^^^^^^^

The manual assessment begins after the deadline of an exercise has passed for all students and is **double-blind**. It means that the tutors do not know the names of the students they assess, and the students do not know the identity of the tutors. The double-blind grading aims to minimize bias and increase the objectivity of the assessment. It implies that both the students and the tutors are **blind** to the identity of each other, ensuring that their expectations or biases do not influence the results. 
    
    .. figure:: assessment/double-blind.png
            :alt: Double-blind grading
            :align: center
   
.. _exercise_complaints:

Complaints
^^^^^^^^^^

After receiving a grade, students can complain about an exercise assessment if the instructor enabled this option, the complaint deadline is still ongoing, and the students think the evaluation needs to be revised. 
The instructor can set a maximum number of allowed complaints per course. These so-called tokens are used for each complaint. The token is returned to the student if the tutor accepts the complaint. 
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


3. By interacting with the respective "Exercise Dashboard" button, the reviewer opens the exercise-specific dashboard and assess students' submissions. 
   Upon evaluation, the reviewer puts a lock expiring automatically in 24 hours in addition to an option of unlocking manually.

    .. figure:: assessment/exercise-dashboard.png
            :alt: Exercise Dashboard
            :align: center

4. For each submission, the reviewer decides on the student's complaint.

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

5. After receiving the complaint result, the student gives feedback to the reviewer.
   
    .. figure:: assessment/student-feedback.png
            :alt: Student Feedback
            :align: center

.. _exercise_more_feedback_request:

More Feedback Requests
^^^^^^^^^^^^^^^^^^^^^^

Another possibility after receiving an assessment is the *More Feedback Request*.
Unlike complaints, they do not cost a token, but the tutor cannot change the score after a feedback request.

    .. figure:: assessment/more-feedback.png
            :alt: Exercise Dashboard
            :align: center

For the reviewers, the process is identical to the complaint process.

.. warning::
    Sending a *More Feedback Request* removes the option to complain about the assessment entirely.
    The score cannot be changed even if the tutor made a mistake during the first assessment and acknowledges this during the *More Feedback Request*.


Grading Leaderboard
^^^^^^^^^^^^^^^^^^^

Artemis also offers a way for instructors to monitor the tutors' assessments based on the students' feedback on reviewer evaluation. The first part of this is the grading leaderboard, which is visible to all tutors. 
    
    .. figure:: assessment/leaderboard.png
            :alt: Grading leaderboard
            :align: center
   
The leaderboard shows the number of assessments each tutor has done and the number of feedback requests and accepted complaints about them.
It also shows the average score the tutor has given and the average rating they received for their assessments. It helps to track and display the performance and rankings of the reviewers who assess and provide feedback on student submissions.

Automatic Assessment
--------------------

Automatic assessment is available for programming and quiz exercises.
For quiz exercises this is the only mode of assessment available. Artemis automatically grades students' submissions after the quiz deadline has passed. See the section about :ref:`quiz` for more information about this.

For programming exercises, this is done via instructor-written test cases that are run for each submission either during or after the deadline. See the section about :ref:`programming` for detailed information about this.
Instructors can enable complaints for automatically graded programming exercises.
