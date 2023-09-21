.. _lti:

LTI Integration
=====================================

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

LTI (Learning Tools Interoperability) is a standard developed by `IMS Global <https://www.1edtech.org/>`_ that allows different learning platforms and tools to work together seamlessly.
It enables tools to integrate smoothly with a learning management system (like Moodle or edX). Artemis supports `LTI 1.1 (deprecated) <https://www.imsglobal.org/specs/ltiv1p1/implementation-guide>`_ and `LTI 1.3. <https://www.imsglobal.org/spec/lti/v1p3>`_
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

LTI 1.3 Instructor Guide
---------------------------
Instructors can configure Artemis online courses through LTI to LMSs.
Below given steps for how to configure Artemis over LTI with Moodle.

Enable Online Course for LTI Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Before diving into the LTI configuration, it's crucial to ensure that the Online Course setting is enabled in Artemis. This setting activates the LTI configurability, allowing instructors to link Artemis with Moodle or other LMS platforms.

Artemis Steps
"""""""""""""
To enable the Online Course setting in Artemis, follow the steps below:\

#. Access Course Management: Start by logging into your Artemis account. From the main dashboard, locate and click on the |course-management| option. This section provides an overview of all the courses you're handling.\
#. Select the Desired Course: From the list of available courses, navigate to the course you wish to configure for LTI integration.\
#. Edit Course Settings: On the course overview page, you'll find an |course_edit| button, located at the top right corner. Clicking this will allow you to modify various course settings.\
#. Locate the Online Course Checkbox: As you scroll through the course settings, you'll come across a checkbox labeled "Online Course." This particular setting is essential for enabling LTI configurability.\
#. Activate LTI Configuration: To finalize the process, simply check the "Online Course" checkbox. By doing so, you're activating the LTI configuration settings for that specific course. Make sure to save any changes made.\

.. figure:: lti/enable_onlinecourse.png
    :align: center
    :width: 500
    :alt: Enable Online Course

With the Online Course setting enabled, you can now proceed to integrate Artemis with Moodle using the LTI 1.3 standard. The subsequent sections of this guide will provide detailed steps on achieving this integration.

Configure an Online Course
^^^^^^^^^^^^^^^^^^^^^^^^^^
To ensure a seamless integration of Artemis with Moodle using the LTI 1.3 standard, specific configurations are required. This section provides a step-by-step guide to achieve this integration.

Artemis Steps
"""""""""""""

To set up the LTI 1.3 integration in Artemis, follow the steps outlined below:

#. Access Course Management: Begin by logging into your Artemis account. From the main dashboard, click on the  |course-management| option.\
#. Choose the Relevant Course: From the list of courses, select the one you wish to configure for LTI integration. This will lead you to the course's settings and details.\
#. Navigate to Course Details: Once inside the course settings, scroll down until you find the Course Details section.\
#. Access LTI Configuration: Within the Course Details section, you'll find an option labeled LTI Configuration. Click on it to access the LTI settings for the course.\

    .. figure:: lti/lticonfiguration_link.png
        :align: center
        :width: 500
        :alt: Locate LTI Configuration

#. Switch to LTI 1.3 Tab: Inside the LTI Configuration, there will be multiple tabs related to different LTI versions. Click on the LTI 1.3 tab to access the settings specific to this version.\
#. Retrieve Dynamic Registration URL: In the LTI 1.3 settings, locate the Dynamic Registration URL. This URL is essential for integrating Artemis with Moodle. Copy this URL for use in the subsequent Moodle configuration steps.\

.. figure:: lti/lticonfiguration_tab.png
    :align: center
    :width: 500
    :alt: LTI 1.3 Configuration

Moodle Steps
"""""""""""""

With the Dynamic Registration URL copied, you can now configure the LTI 1.3 integration in Moodle:

#. Access Site Administration: Log into your Moodle account. From the main dashboard, navigate to the Site Administration section. This section contains various administrative settings for the Moodle platform.
#. Navigate to External Tool Settings: Inside the Site Administration, go to Plugins. From there, select External tool followed by Manage Tools. This will lead you to the LTI configurations in Moodle.
#. Enter Dynamic Registration URL: In the "Manage Tools" section, you'll find a field labeled "Tool URL." Paste the previously copied "Dynamic Registration URL" from Artemis into this field.
#. Initiate LTI Advantage Integration: After entering the URL, click on the Add LTI Advantage button. This action will begin the process of integrating Artemis with Moodle using the LTI 1.3 standard.
#. Locate the Artemis Course: Once the integration process starts, scroll down the list until you find the Artemis course identified by its shortname.
#. Activate the Integration: To finalize the integration, click on the Activate button next to the Artemis course name. This action will complete the LTI 1.3 integration between Artemis and Moodle.

Link Artemis Exercises to Moodle Course
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis Steps
"""""""""""""

#. Navigate to the course management section.\
#. Select the corresponding course.\
#. Scroll down to Course Details.\
#. Route to LTI Configuration.\
#. Navigate to Exercises tab.\
#. Copy the LTI 1.3 Launch URL for the respected exercise.

Moodle Steps
"""""""""""""
#. Navigate to corresponding course.\
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

.. |course-management| image:: exercises/general/course-management.png
.. |course_edit| image:: courses/customizable/buttons/course_edit.png
