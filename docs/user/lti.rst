.. _lti:

LTI Integration
=====================================

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

LTI (Learning Tools Interoperability) is a standard developed by IMS Global that allows different learning platforms and tools to work together seamlessly.
It enables tools to integrate smoothly with a learning management system (like Moodle or edX). Artemis supports LTI 1.1 (deprecated) and LTI 1.3.
The table below showcases the types of exercises supported by Artemis and their respective functionalities.

.. list-table:: Supported Exercise Types
   :widths: 25 15 15 15
   :header-rows: 1

   * - Exercise Type
     - Launch Exercise
     - View Assessment Result
     - View Assessment Feedback
   * - Programming exercise
     - ✔
     - ✔
     - ✔
   * - Quiz exercise
     - ✔
     - ✔
     - ✔
   * - Modeling exercise
     - ✔
     - ✔
     - ✔
   * - Text exercise
     - ✔
     - ✔
     - ✔
   * - File Upload exercise
     - ✔
     - ✔
     - ✔

LTI 1.3 Supported Use-cases
---------------------------
Instructors can configure Artemis online course through LTI to LMSs.
Below presented use-cases that are supported for Artemis Moodle integration.

.. list-table:: Artemis LTI 1.3 Instructor Use Case
   :widths: 25 50 50
   :header-rows: 1

   * - Uses Case
     - Artemis Steps
     - Moodle Steps
   * - Enable Online Course
     - #. Navigate to Course Management.\
       #. Navigate to Course.\
       #. Go to Edit.\
       #. Scroll down until you see the Online Course checkbox.\
       #. Check online course checkbox.
     - N/A
   * - Configure an Online Course
     - #. Navigate to the course management section.\
       #. Select the corresponding course.\
       #. Scroll down to Course Details.\
       #. Route to LTI Configuration.\
       #. Navigate to LTI 1.3 tab.\
       #. Copy the Dynamic Registration URL.\
     - #. Navigate to Site Administration.\
       #. Select Plugins → External tool → Manage Tools.\
       #. Paste the Dynamic Registration URL to Tool URL field
       #. Click on "Add LTI Advantage".\
       #. Scroll down to find the Artemis course with its shortname.\
       #. Click on "Activate" to complete the integration.
   * - Link Artemis Exercises to Moodle Course
     - #. Navigate to the course management section.\
       #. Select the corresponding course.\
       #. Scroll down to Course Details.\
       #. Route to LTI Configuration.\
       #. Navigate to Exercises tab.\
       #. Copy the LTI 1.3 Launch URL for the respected exercise.
     - #. Navigate to corresponding course.\
       #. Enable edit mode.\
       #. Press "Add an activity or resource".\
       #. Select external tool.\
       #. Paste the copied exercise URL on the resource URL field.\
       #. Save and go to course.


.. list-table:: Artemis LTI 1.3 Student Use Cases
   :widths: 25 50
   :header-rows: 1

   * - Uses Case
     - Moodle Steps
   * - Start Artemis Exercise
     - #. Navigate to Moodle Course.\
       #. Select external exercise to participate.\
       #. Artemis exercise page opens through an window inside Moodle.\
       #. If it is the first time that student participates an Artemis exercise a pop-up appears.\
       #. Given generated password will be used to sign in to Artemis in the future.\
       #. Copy generated password to a safe place and close the pop-up.\
       #. Participate the Artemis Exercise.
   * - View Results for Artemis Exercises
     - #. Navigate to Grades tab.\
       #. Verify grades and feedback for evaluated Artemis exercises.
