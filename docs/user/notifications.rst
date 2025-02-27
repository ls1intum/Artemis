.. _notifications:

Notifications
=============

.. contents:: Content of this document
    :local:
    :depth: 2

Artemis supports customizable web and email notifications. Users can enable and disable different notification types.

Web Notifications
^^^^^^^^^^^^^^^^^

The web notifications can be found on the top right of the page by clicking on the bell icon.
A red indicator shows the number of new messages.

|notification-top-bar|

|notification-side-bar|

Notifications About Incoming Messages and Replies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This video shows how web notifications works for incoming messages and replies:

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/40578?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Watch this video on TUM-Live.
    </iframe>

Email Notifications
^^^^^^^^^^^^^^^^^^^

Artemis can also send out emails for certain notification types.
Additionally, Artemis can send out a weekly summary at Friday 5pm.

|notification-email|

Push Notifications
^^^^^^^^^^^^^^^^^^

Artemis can also send out push notification to the Artemis native iOS and Android apps.
To support push notifications admins have to explicitly activate this in the artemis configuration.

This notifications are e2e encrypted and sent via the TUM hosted Hermes service ( https://hermes.artemis.cit.tum.de/ ).
Users explicitly have to opt-in via their mobile application to receive push notifications and can deactivate them at any time.

|notification-push|

Overview
^^^^^^^^

The following tables gives an overview of all supported notification types:

.. list-table:: Notification Types
   :widths: 20 10 10 10
   :header-rows: 1

   * - NotificationType
     - Push
     - Web
     - Email

   * - **Course-Wide Discussion Notifications**
     -
     -
     -

   * - NEW_COURSE_POST
     - X
     - X
     -

   * - NEW_REPLY_FOR_COURSE_POST
     - X
     - X
     -

   * - NEW_ANNOUNCEMENT_POST
     - X
     - X
     - X

   * - **Exercise Notifications**
     -
     -
     -

   * - EXERCISE_RELEASED
     - X
     - X
     - X

   * - EXERCISE_PRACTICE
     - X
     - X
     - X

   * - EXERCISE_SUBMISSION_ASSESSED
     - X
     - X
     - X

   * - FILE_SUBMISSION_SUCCESSFUL
     - X
     - X
     - X

   * - NEW_EXERCISE_POST
     - X
     - X
     -

   * - NEW_REPLY_FOR_EXERCISE_POST
     - X
     - X
     -

   * - **Lecture Notifications**
     -
     -
     -

   * - ATTACHMENT_CHANGE
     - X
     - X
     - X

   * - NEW_LECTURE_POST
     - X
     - X
     -

   * - NEW_REPLY_FOR_LECTURE_POST
     - X
     - X
     -

   * - **New message/replies Notifications**
     -
     -
     -

   * - CONVERSATION_NEW_MESSAGE
     - X
     - X
     -

   * - CONVERSATION_NEW_REPLY_MESSAGE
     - X
     - X
     -

   * - CONVERSATION_USER_MENTIONED
     - X
     - X
     - X

   * - CONVERSATION_CREATE_GROUP_CHAT
     - X
     - X
     -

   * - CONVERSATION_ADD_USER_CHANNEL
     - X
     - X
     -

   * - CONVERSATION_ADD_USER_GROUP_CHAT
     -
     - X
     -

   * - CONVERSATION_REMOVE_USER_GROUP_CHAT
     -
     - X
     -

   * - CONVERSATION_REMOVE_USER_CHANNEL
     -
     - X
     -

   * - CONVERSATION_DELETE_CHANNEL
     -
     - X
     -

   * - **Tutorial Group Notifications**
     -
     -
     -

   * - TUTORIAL_GROUP_REGISTRATION_STUDENT
     - X
     - X
     - X

   * - TUTORIAL_GROUP_DEREGISTRATION_STUDENT
     - X
     - X
     - X

   * - TUTORIAL_GROUP_DELETED
     - X
     - X
     - X

   * - TUTORIAL_GROUP_UPDATED
     - X
     - X
     - X

   * - **Tutor Notifications**
     -
     -
     -

   * - TUTORIAL_GROUP_REGISTRATION_TUTOR
     - X
     - X
     - X

   * - TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR
     - X
     - X
     - X

   * - TUTORIAL_GROUP_DEREGISTRATION_TUTOR
     - X
     - X
     - X

   * - TUTORIAL_GROUP_ASSIGNED
     - X
     - X
     - X

   * - TUTORIAL_GROUP_UNASSIGNED
     - X
     - X
     - X

   * - **Editor Notifications**
     -
     -
     -

   * - PROGRAMMING_TEST_CASES_CHANGED
     -
     - X
     -

   * - **Instructor Notifications**
     -
     -
     -

   * - COURSE_ARCHIVE_STARTED
     - X
     -
     -

   * - COURSE_ARCHIVE_FINISHED_WITHOUT_ERRORS
     -
     - X
     -

   * - COURSE_ARCHIVE_FINISHED_WITH_ERRORS
     -
     - X
     -

   * - COURSE_ARCHIVE_FAILED
     -
     - X
     -

   * - EXAM_ARCHIVE_STARTED
     -
     - X
     -

   * - EXAM_ARCHIVE_FINISHED_WITHOUT_ERRORS
     -
     - X
     -

   * - EXAM_ARCHIVE_FINISHED_WITH_ERRORS
     -
     - X
     -

   * - EXAM_ARCHIVE_FAILED
     -
     - X
     -

   * - **Unassigned Notifications**
     -
     -
     -

   * - EXERCISE_UPDATED
     -
     - X
     -

   * - QUIZ_EXERCISE_STARTED
     - X
     - X
     -

   * - DUPLICATE_TEST_CASE
     - X
     - X
     - X

   * - ILLEGAL_SUBMISSION
     -
     - X
     -

   * - NEW_PLAGIARISM_CASE_STUDENT
     - X
     - X
     - X

   * - NEW_CPC_PLAGIARISM_CASE_STUDENT
     - X
     - X
     - X

   * - PLAGIARISM_CASE_VERDICT_STUDENT
     - X
     - X
     - X

   * - PLAGIARISM_CASE_REPLY
     - X
     - X
     - X

   * - NEW_MANUAL_FEEDBACK_REQUEST
     -
     - X
     -

   * - DATA_EXPORT_CREATED
     - X
     - X
     - X

   * - DATA_EXPORT_FAILED
     - X
     - X
     - X


For the exact contents sent for each notification, please check out the usages of the `NotificationPlaceholderCreator` interface in the code.


Settings
^^^^^^^^

The user can change their preference for different types of notifications and decide if they want to receive emails, web notifications, push notifications or no notification.
These settings can be found after opening the web notifications. The gear on the top left of the sidebar then leads to the settings.
The push notification settings can currently only be found in the respective application.

|notification-settings|
|notification-settings-mobile|

.. |notification-top-bar| image:: notifications/top-bar.png
    :width: 500
.. |notification-side-bar| image:: notifications/side-bar.png
    :width: 500
.. |notification-email| image:: notifications/email.png
    :width: 1000
.. |notification-settings| image:: notifications/settings.png
    :width: 1000
.. |notification-settings-mobile| image:: notifications/notification-settings-mobile.jpeg
    :width: 300
.. |notification-push| image:: notifications/notification-push.png
    :width: 300
.. |supported-notification-types-overview-1| image:: notifications/supported-notification-types-overview-1.png
    :width: 1000
.. |supported-notification-types-overview-2| image:: notifications/supported-notification-types-overview-2.png
    :width: 1000
.. |supported-notification-types-overview-3| image:: notifications/supported-notification-types-overview-3.png
    :width: 1000
