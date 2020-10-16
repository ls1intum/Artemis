===================
Instructors’ Guide:
===================

Phases of Artemis Online Exam
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- **Exam Creation and Configuration**
    During the exam creation and configuration, you can create your exam and configure it to fit your needs. Add exercises with different variants, register students, generate student exams. For more information see `Create and Configure Exam`_.
- **Exam Conduction**
    The exam conduction starts when the exam becomes visible to the students and ends when the latest working time is over. When the exam conduction begins, you cannot make any changes anymore to the exam configuration or individual student exams. 
- **Exam Assessment**
    The assessment begins as soon as the latest student exam working time is over. During this period, your team can assess the submissions of the students and provide results. The testing suites for programming exercises are executed and you can evaluate the quiz exercises automatically, see `Assessing Student Exams and Complaints`_. The exam assessment ends, when the results are published, see `Create and Configure Exam`_.
- **Exam Student Review**
    The student review period is set by the exam configuration, see `Create and Configure Exam`_. The students can view their results as soon as they are published, but during the review period then can submit complaints about perceived mistakes made during the exam assessment. A second assessor, other than the original one will have the opportunity to review the complaint and respond to it. The results are then updated automatically. 


Accessing the Exam Management Page
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Log in to Artemis with your account credentials.
- Head to ``Course Management``.
- Click on ``Create Exam`` for your course. It will open the *Exam Management Screen*.

    - Here you have access to all the exams of your course. All aspects of the exam are managed from the management screen. 

- You can create an exam by clicking on ``Create new Exam``. 

Create and Configure Exam
^^^^^^^^^^^^^^^^^^^^^^^^^
- When you click on ``create an exam`` you are presented with the *Create Exam* view. Here you can set the basic information such as ``title``, ``examiner`` etc. You also can set the timeline of the exam. This is defined by the dates: ``visible from``, ``start of working time``, ``end of working time``, ``release date of results``, ``begin of student review``, ``end of student review``. 
- The first three dates are mandatory whereas you can define the rest when it suits best. 
- The ``grace period`` defines the amount of time the students have at their disposal to hand in their exam, after the ``working time`` is over. This is set to 3 minutes by default. 
- You can also define the ``number of exercises`` in the exam. You can leave this out initally, however it must be set before you can generate the student exams. For more information see `Exercise Groups`_. 
- If you activate ``randomize order of exercise groups``, the order of the exercises will be random for each student. 
- Finally, you can fill out the ``exam start-`` and ``end texts``. These will be displayed during the exam conduction to student, at the *Start-* and *End* page respectively.

.. figure:: instructor/exam_configuration.png
   :alt: Create and Configure
   :align: center

   Create and Configure the Exam

Exercise Groups
^^^^^^^^^^^^^^^
- Exercise groups represent an individual exercise slot for each student exam. Artemis exam mode allows you to define multiple exercise variants so that each student can receive a unique exam. This is done through the exercise groups. Within one exercise group you can define different exercises. 
- When the student exams are generated, one exercise will be selected per exercise group. This can be tweaked further as you can distinguish between ``mandatory`` exercise groups and ``non-mandatory`` exercise groups. This is set initially, when you create an exercise group. By default, every exercise group is mandatory. You can edit this by clicking ``Edit`` on the exercise group.
- Depending on the ``number of exercises`` set in the exam configuration, see `Create and Configure Exam`_, if you have more exercise groups then the number set, no exercise from the ``non-mandatory`` exercise groups will be added to the student exams. 
- Once you have created an exercise group you can start adding exercises. 

.. figure:: instructor/exercise_variants.png
   :alt: Exercise Groups with different Exercise Variants
   :align: center

   Exercise Groups with different Exercise Variants

Add Exercises
^^^^^^^^^^^^^
- Exercises are grouped by exercise groups. For every student exam, one exercise per exercise group will be chosen, see `Exercise Groups`_. 

     - **Hint:** If you want all student to have the same exam, define only one exercise per exercise group.

- To add exercises navigate to the *Exercise Groups* of the exam. On the header of each exercise group you will find the available exercise types. You can choose between ``creating a new exercise`` or ``importing an existing one`` from your courses. 

    - **Hint:** For programming exercises you can check the option to allow manual assessment. 

- For exercise types ``text``, ``programming``, and ``modeling``, you can also define example submissions and example assessments to guide your assessor team.

.. figure:: instructor/add_exercises.png
   :alt: Add different Exercises
   :align: center

   Add different Exercises

Registering Students
^^^^^^^^^^^^^^^^^^^^
- To register students to the exam, navigate from the exam management to the *“Students”* page. Here you are presented with two options to register students. You can: 

    1. Add students manually my searching via the search bar
    2. Bulk import students using a ``.csv`` file. You can do this by pressing the ``Import Students`` button.

- You can also choose to remove students from the exam. When you do so, you have the option to also delete their participations and submissions linked to the user’s student exam. 
    
    - **Hint:** Just registering the students to the exam will not allow them to participate in the exam. First, individual student exams must be generated. For more information see `Manage Student Exams`_.

.. figure:: instructor/add_students.png
   :alt: Register Students
   :align: center

   Register Students Page

Manage Student Exams
^^^^^^^^^^^^^^^^^^^^
-  The student exams are managed via the *“Student Exams”* page from the Exam Management. 
- Here you can have an overview of all student exams. When you press ``View`` on a student exam, you can view the ``details of the student``, the allocated ``working time``, his/her ``participation status``, their ``summary`` as well as their *scores*. Additionally, you will also be able to view which assessor is responsible for each exercise. 

    - You can also change the individual ``working time`` of students should this be necessary. 

- To generate the student exams, you must click on ``Generate individual exams``. This will automatically create a student exam for every registered user. The number of exercises will be determined by the exam configuration set, see `Create and Configure Exam`_ whereas the exercises will be randomly selected from the available exercise variants per exercise group, see `Exercise Groups`_.
- The ``Generate individual exams`` button will be locked once the exam becomes visible to the students. You cannot perform changes to the student exams once the exam conduction has started. 
- If you have added more students recently, you can choose to ``Generate missing individual exams``. 
- ``Prepare exercise start`` creates a participation for each exercise for every registered user, based on their assigned exercises. It also creates the individual repositories and build plans for programming exercises. This action can take a while if there are many registered students due to the communication between the VC and CI server. 

    - **Warning:** ``Prepare exercise start`` must be executed before the exam conduction begins. 

- On the *"Student Exams"* page, you can also maintain the repositories of the student exams. You can choose to ``lock the repositories`` and ``unlock`` them.
- Additionally, once the exam is over you can click on ``Evaluate quizzes``. This action will evaluate all student exam submissions for all quiz exercises and assign an automatic result. 

   - **Hint:** If you do not press this button, the students quiz exercise will not be graded.

.. figure:: instructor/student_exams.png
   :alt: Student Exam Page
   :align: center

   Student Exam Page

Conducting Test Runs
^^^^^^^^^^^^^^^^^^^^
- Test runs are designed to offer the instructors confidence that the exam conduction will run smoothly. They allow you to experience the exam from a student’s perspective. A ``test run`` is distinct from a ``student exam`` and is not taken into consideration for the Tutor *"Exam Dashboard"*, *"Student Participations"* and *"Exam Scores"*. 
- You can manage your test runs from the *"Test Run"* page.
- To create a new test run you can press ``Create a Test Run``. This will open a modal where you can select an exercise for each exercise group. You can also set the ``working time``. Test runs only follow the exercise groups created and do not take the exam configuration for ``number of exercises`` under consideration. 

    - **Hint:** Exercise groups with no exercises are ignored.

- When you start the test run, you conduct the exam similar to how a student would. You can create submissions for the different exercises and end the test run. 
- If you have a completed test run, you can assess your submissions. You can access this using the button *“Assess your Test Runs”*. 

    - **Hint:** Only the creator of the test run is able to assess his submissions.

- The results can be viewed by clicking on ``Summary``. This page simulates the *"Student Exam Summary"* where the students can view their submissions and view the results once they are published. 
- For assessed test run submissions, you can also use the ``complaint`` feature. This feature allows students to request a review of an assessment during the student review period, see `Create and Configure Exam`_ 

Assessing Student Exams and Complaints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- Once the exam conduction is over and the latest individual ``working time`` has passed, see `Manage Student Exams`_, your team can begin the assessment process. 
- This is done through the *“Tutor Exam Dashboard”*. 

    - **Hint:** If the exam is not over, you will not be able to access this page.

- The assessment process is anonymised. All student information will not be displayed to the assessors.
- The Tutor Exam dashboard provides an overview over the current assessment progress. This is divided by exercises and for each exercise, you can view how many submissions have already been assessed and how many are still left. The status of the student complaints is also displayed here. 

    - **Hint:** To check for plagiarism, you must navigate to the individual exercise. This can be done by navigating to:

     *Exam Management* -> *Exercise Groups* -> *View* on the specific exercise.

     At the bottom of the page you will find the option ``check for plagiarism``.

.. figure:: instructor/tutor_dashboard.png
   :alt: Tutor Exam Dashboard
   :align: center

   Tutor Exam Dashboard

- To assess a submission for an exercise, you can click on ``Exercise Dashboard``.
- First you must go through the example submissions and assessments to review how a specific exercise should be evaluated. 
- If there is a submission which has not been assessed yet, you can click ``Start new assessment``. This will fetch a random student submission of this exercise which you can then assess.
- Programming exercises are graded automatically but if ``manual assessment`` is allowed, see `Add Exercises`_, you can review and enhance the automatic results. Programming exercise submissions with manual assessment allowed are accessed as described above.

.. figure:: instructor/programming_assessment.png
   :alt: Programming Submission Assessment
   :align: center

   Manually Assessing a Programming Submission

- Quiz exercises are graded automatically via the student exam page, see `Manage Student Exams`_, and therefore do not appear in the *"Tutor Exam Dashboard"*.
- Once the student review period begins students can complain about their results. You can evaluate these complaints in the *"Tutor Exam Dashboard"*. All complaints are listed below the submissions. The original assessor may not respond to the complaint, this must be done by a second assessor. 

Exam Scores
^^^^^^^^^^^
- You can view the exam scores from the *“Scores”* page. This view aggregates the results of the students and combines them to provide an overview over the students’ performance. 
- You can view the spread between different achieved scores, the average results per exercise as well as the individual students' results.
- Additionally, you can choose to modify the dataset by selecting ``only include submitted exams`` or ``only include exercises with at least one non-empty submission``.
- The exam scores can also be exported via ``Export Results as CSV``.

.. figure:: instructor/exam_statistics.png
   :alt: Exam Scores page
   :align: center

   Exam Scores Page