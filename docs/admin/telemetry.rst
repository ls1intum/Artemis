.. _telemetry:

Telemetry
=========

To help to improve Artemis, we collect some data when the application starts.
This feature can be disabled by setting `telemetry.enabled` in the `application-prod.yml` to `false`.
When this is set to false, no data is sent to the Artemis maintainer team.
By setting `telemetry.sendAdminDetails` to false, personal information of the instance's admin (i.e. contact email and name) is excluded from the telemetry data.
This includes the contact email and the administrator's name.

Artemis collects the following data at the startup of an instance:

* The used Artemis version
* The contact email address of the admin, which is set in `info.contact`
* The name of the admin, set in `info.operatorAdminName` (optional)
* The server's URL
* The operator's name
* The used profiles (e.g. Gitlab, Jenkins, LocalVC, Aeolus, ...)

Example configuration in `application-prod.yml`:

.. code-block:: yaml

    artemis:
        telemetry:
            enabled: true
            sendAdminDetails: false
            destination: https://telemetry.artemis.cit.tum.de

    info:
        contact: contactMailAddress@cit.tum.de
        operatorName: Technical University of Munich
        operatorAdminName: Stephan Krusche

We collect this data to enhance Artemis by understanding how it is used, ensuring compatibility with different configurations, and providing better support to our users.
Collecting admin contact information allows us to communicate important updates or address critical issues directly.
