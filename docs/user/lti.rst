.. _lti:

LTI (Learning Tool Interoperability)
=====================================

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

LTI (Learning Tools Interoperability) is a standard developed by IMS Global that allows different learning platforms and tools to work together seamlessly.
It enables tools to integrate smoothly with a learning management system (like Moodle or edX). Artemis supports LTI 1.1 (deprecated) and LTI 1.3.

.. list-table:: Supported Exercise Types
   :widths: 25 15 15 15
   :header-rows: 1

   * - Exercise Type
     - Launch Exercise
     - View Assessment Result
     - View Assessment Feedback
   * - Programming exercise
     - [x]
     - [x]
     - [x]
   * - Quiz exercise
     - [x]
     - [x]
     - [x]
   * - Modeling exercise
     - [x]
     - [x]
     - [x]
   * - Text exercise
     - [x]
     - [x]
     - [x]
   * - File Upload exercise
     - [x]
     - [x]
     - [x]

LTI 1.3 Supported Use-cases
---------------------------
Instructors can configure Artemis online course through LTI to LMSs.
Below presented use-cases that are supported for Artemis Moodle integration.

.. list-table:: Artemis LTI 1.3 Instructor Use Cases
   :widths: 25 50 50
   :header-rows: 1

   * - Uses Case
     - Artemis Steps
     - Moodle Steps
   * - Enable Online Course
     - 1. Navigate to Course Management.\
       2. Navigate to Course.\
       3. Go to Edit.\
       4. Scroll down until you see the Online Course checkbox.\
       5. Check online course checkbox.
     -
   * - Configure an Online Course
     - 1. Navigate to the course management section.\
       2. Select the corresponding course.\
       3. Scroll down to Course Details.\
       4. Route to LTI Configuration.\
       5. Navigate to LTI 1.3 tab.\
       6. Copy the Dynamic Registration URL.\
     - 1. Navigate to Site Administration.\
       2. Select Plugins → External tool → Manage Tools.\
       3. Paste the Dynamic Registration URL to Tool URL field
       4. Click on "Add LTI Advantage".\
       5. Scroll down to find the Artemis course with its shortname.\
       6. Click on "Activate" to complete the integration.
   * - Link Artemis Exercises to Moodle Course
     - 1. Navigate to the course management section.\
       2. Select the corresponding course.\
       3. Scroll down to Course Details.\
       4. Route to LTI Configuration.\
       5. Navigate to Exercises tab.\
       6. Copy the LTI 1.3 Launch URL for the respected exercise.
     - 1. Home → Select the corresponding course → Enable edit mode.\
       2. Press "Add an activity or resource".\
       3. Select external tool.\
       4. Paste the recently copied exercise URL on the resource URL field.\
       5. Save and go to course.


.. list-table:: Artemis LTI 1.3 Student Use Cases
   :widths: 25 50
   :header-rows: 1

   * - Uses Case
     - Moodle Steps
   * - Start Artemis Exercise
     - 1. Navigate to Moodle Course.\
       2. Select external exercise to participate.\
       3. Artemis exercise page opens through an window inside Moodle.\
        3.1. If it is the first time that student participates an Artemis exercise a pop-up appears.\
        3.2. Given generated password will be used to sign in to Artemis in the future.\
        3.3. Copy generated password to a safe place and close the pop-up.\
       5. Participate the Artemis Exercise.
   * - View Results for Artemis Exercises
     - 1. Navigate to Grades tab.\
       2. Verify grades and feedback for evaluated Artemis exercises.
