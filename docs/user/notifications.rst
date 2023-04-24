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

|supported-notification-types-overview-1|
|supported-notification-types-overview-2|
|supported-notification-types-overview-3|

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
