.. _telemetry:

Telemetry
=========

To help to improve Artemis, we collect some data, once at the application startup.
This feature can be disabled by setting `telemetry.enabled` in the `application-prod.yml` to `false`.
When this is set to false, no data is sent to Artemis.
By setting `telemetry.sendAdminDetails` to false, personal information of the instance's admin is excluded from the telemetry data.
This includes the contact email and the admins name.

Artemis collects the following data at the startup of an instance:

* The used Artemis version
* The contact email address of the admin, which is set in `info.contact`
* The name of the admin, set in `info.universityAdminName` (optional)
* The server's URL
* The university's name
* The used profiles (e.g. Gitlab, Jenkins, LocalVC, Aeolus, ...)

Example configuration in `application-prod.yml`:

.. code-block:: yaml

    artemis:
        telemetry:
            enabled: true
            sendAdminDetails: false
            destination: telemetry.artemis.cit.tum.de
