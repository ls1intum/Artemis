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
2. Review: Reviewers access the submitted work and review it carefully, considering the objectives, requirements, and criteria established for the assessment.
3. Evaluation: Based on their assessment, reviewers assign scores, provide feedback, or grade the student's work keeping the grading criteria in mind to ensure consistency and fairness.
4. Student feedback: Students rate the quality of the feedback to motivate the reviewers to provide high-quality feedback to improve understanding and prevent misconceptions.
            

Assessment Dashboard
^^^^^^^^^^^^^^^^^^^^

To keep track of the manual assessments, Artemis offers the assessment dashboard.
It shows the assessment progress of each exercise by showing the state of the exercise, the total number of submissions, the number of submissions that have been assessed, and the number of complaints and more feedback requests.
It also shows the average rating the students have given to each exercise.

.. figure:: assessment/assessment_dashboard.png
    :alt: A picture of the assessment dashboard (of an exam)
    :align: center

Each exercise also has its own assessment dashboard that shows all of this information for a single exercise.

Structured Grading Criteria
^^^^^^^^^^^^^^^^^^^^

To ensure consistency, fairness, and transparency and simplify the grading process, Artemis provides structured grading instructions (comparable to grading rubrics) that can be dragged and dropped, making it easier and faster to provide feedback. They include predefined feedback and points so that different reviewers can follow the same criteria when assessing student work. Additionally, they provide transparency to students, allowing them to understand how reviewers evaluate their submissions.

.. figure:: assessment/grading_criteria.png
    :alt: A picture of the assessment user interface with grading criteria, student submission and example solution
    :align: center

Integrated Training Process
^^^^^^^^^^^^^^^^^^^^

Instructors can use the assessment training process to make the grading more consistent. An integrated training process for reviewers based on example submissions and example assessments ensures that reviewers have enough knowledge to properly assess submissions and provide feedback. They define a series of example submissions and assessments that the tutors must first read through.

Grading Leaderboard
^^^^^^^^^^^^^^^^^^^^

Artemis also offers a way for instructors to monitor the tutors' assessments based on the students' feedback on reviewer evaluation. The first part of this is the grading leaderboard, which is visible to all tutors. 
    
.. figure:: assessment/leaderboard.png
   :alt: Grading leaderboard
   :align: center

The leaderboard shows the number of assessments each tutor has done and the number of feedback requests and accepted complaints about them.
It also shows the average score the tutor has given and the average rating they received for their assessments. It helps to track and display the performance and rankings of the reviewers who assess and provide feedback on student submissions.

Double-blind Grading
^^^^^^^^^^^^^^^^^^^^

The manual assessment begins after the deadline of an exercise has passed for all students and is **double-blind**. It means that the tutors do not know the names of the students they assess, and the students do not know the identity of the tutors. The double-blind grading aims to minimize bias and increase the objectivity of the assessment. It implies that both the students and the tutors are "blind" to the identity of each other, ensuring that their expectations or biases do not influence the results. 
    
.. figure:: assessment/double_blind.png
   :alt: Double-blind grading
   :align: center

Automatic Assessment
--------------------

Automatic assessment is available for programming and quiz exercises.
For quiz exercises this is the only mode of assessment available. Artemis automatically grades students' submissions after the quiz deadline has passed. See the section about :ref:`quiz` for more information about this.

For programming exercises, this is done via instructor-written test cases that are run for each submission either during or after the deadline. See the section about :ref:`programming` for detailed information about this.
Instructors can enable complaints for automatically graded programming exercises.


.. _exercise_complaints:

Complaints
----------

After receiving an assessment, students can complain once about the assessment of an exercise if the instructor enabled this option and the students think the assessment is erroneous.
The student has to write an additional text when submitting a complaint to justify the reevaluation.

    .. figure:: assessment/complaint.png
            :alt: The student complains about the assessment of an exercise
            :align: center

A complaint leads to a reevaluation of the submission by another tutor. This tutor sees the existing assessment and the complaint reason. The tutor can then either accept or reject the complaint.
Only if the tutor accepts the complaint, they can modify the assessment's score.

    .. figure:: assessment/complaint_response.png
        :alt: A tutor answers the complaint of a student
        :align: center

The instructor can set a maximum number of allowed complaints per course. These so-called tokens are used for each complaint.
The token is given back to the student if the tutor accepts the complaint.
This means a student can submit as many complaints as they want, as long as they are accepted.


.. _exercise_more_feedback_request:

More Feedback Requests
^^^^^^^^^^^^^^^^^^^^^^

Another possibility after receiving an assessment is the *More Feedback Request*.
Compared to the complaints, they do not cost a token, but the tutor cannot change the score after a feedback request.

.. warning::
    Sending a *More Feedback Request* removes the option to complain about the assessment entirely.
    The score cannot be changed even if the tutor made a mistake during the first assessment and acknowledges this during the *More Feedback Request*.
