**************************
Exam Participation Checker
**************************

.. _exam_participation_checker:

What is it?
===========
The Artemis Exam Participation Checker iPad application is designed to streamline the process of checking student attendance during on-site exams conducted with the Artemis platform. The application replaces the traditional paper-based method with a digitalized process, making tracking and managing student attendance easier. The application includes features such as student identification, digital signing, and attendance tracking. The Artemis web platform was adapted to additionally support exam management tasks,  such as uploading seating arrangements and images for students.

Requirements
============

.. note::
    For information regarding creating the exam, it's exercises and how to register students, please follow the instructions as described in :ref:`exam configuration <exam_creation_and_configuration>`.

To use the Artemis Exam Participation Checker iPad application, additional setup before the exam is required. This includes the following tasks on the *Students* page:

- uploading images for the students. This can be done via the |upload_images| button. An exemplary file can be found here: :download:`pdf <instructor/example_upload_images.pdf>`.
- setting up the room and seating information via the bulk import.

.. note::
    To upload the images each student needs to have a matriculation number assigned. This is used to match the image with the according student.

.. note::
    Seats and rooms can currently only be assigned via the bulk import of students, by defining the seat and room in the ``CSV`` file.

Download
========
The App is currently only available via TestFlight. It can be downloaded via the following link: https://testflight.apple.com/join/hlwcUa0b 

Usage
=====

1.1 Login
^^^^^^^^^
- You can login to the app with your usual Artemis credentials. 
- Via the 'Select University' button at the bottom you can select your respective University instance or a custom Artemis instance of your choice.

1.2 Exam Overview
^^^^^^^^^^^^^^^^^
- The Exam Overview consists of a list of all currently available exams.
- The overview only shows exams where the start date is +/- one week from now and your account has the rights to access the exam.
- Each list cell consists of basic exam information, like the name and the start and end date of the respective exam.
- A tap on the cell opens up the Student List View.

|exam_overview|

1.3 Student List View
^^^^^^^^^^^^^^^^^^^^^
- The sidebar hosts a list of all registered students.
- The list can easily be filtered by room with a clickable picker or by typing a custom search query into the search bar. Further, already checked-in students can be easily filtered out by the respective toggle. 
- The sorting can be quickly changed from "Bottom to Top" to "Top to Bottom" by a picker. 
- A simple counter shows the progress of the already checked-in students directly above the student list.
- The student list itself consists of one cell per student, containing the name and the seat, to enable quick identification of the student. 
- The tutor can quickly switch between different students by clicking the respective cell.
- Below the list is an 'Export Signatures' button to export all local signatures taken on this device for the selected exam.

|student_list_view|

1.4 Student Detail View
^^^^^^^^^^^^^^^^^^^^^
- The detail view contains all the given information about the student. This information is displayed in the upper part of the screen. 
    - The student image is shown on the left side, while all other information is displayed right next to it.
    - Clicking the small pen icon edits the room and seat. In this case, the user can choose between all the available rooms through a picker. The user can change the seat by typing in the respective seat in the text field. 
- A second section starts below the information section, allowing the user to verify the given information via a toggle.
- The lower part of the screen is a large canvas that supports signing with the help of the Apple Pencil or simple input with the finger.
    - On the right of the canvas are two small buttons. The upper button, symbolized by a swiping finger icon, enables a better signing experience with the finger when toggled on. The lower button, a trash symbol, deletes the current signing to restart the process.
- By clicking the save button the data is persisted on the server. Additionally, the signing is persisted locally on the device, which can be exported as mentioned above.

|student_detail_view|


.. |exam_overview| image:: exam-participation-checker/exam_overview.png
.. |student_list_view| image:: exam-participation-checker/student_list_view.png
.. |student_detail_view| image:: exam-participation-checker/student_detail_view.png
.. |upload_images| image:: exam-participation-checker/upload_images.png

