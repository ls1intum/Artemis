.. _telemetry:

Telemetry
=========

To help improve Artemis, we collect some data, once at the application startup.
This feature can be disabled by setting `telemetry.enabled` in the `application-prod.yml` to `false`.
When this is set to false, no data is sent to Artemis.

Artemis collects the following data at the startup of an instance:

* The used Artemis version
* The contact email address of the admin, which is set in `info.contact`
* The name of the admin, set in `info.universityAdminName` (optional)
* The server's URL
* The university's name
* The used profiles (e.g. Gitlab, Jenkins, LocalVC, Aeolus, ...)
