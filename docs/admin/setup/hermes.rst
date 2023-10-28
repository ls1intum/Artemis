Hermes Service
--------------

Push notifications for the mobile Android and iOS clients rely on the Hermes_ service.
To enable push notifications the Hermes service needs to be started separately and the configuration of the Artemis instance must be extended.

Configure and start Hermes
^^^^^^^^^^^^^^^^^^^^^^^^^^

To run Hermes, you need to clone the `repository <https://github.com/ls1intum/Hermes>`_ and replace the placeholders within the ``docker-compose`` file.

The following environment variables need to be updated for push notifications to Apple devices:

* ``APNS_CERTIFICATE_PATH``: String - Path to the APNs certificate .p12 file as described `here <https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/establishing_a_certificate-based_connection_to_apns>`_
* ``APNS_CERTIFICATE_PWD``: String - The APNS certificate password
* ``APNS_PROD_ENVIRONMENT``: Bool - True if it should use the Production APNS Server (Default false)

Furthermore, the <APNS_Key>.p12 needs to be mounted into the Docker under the above specified path.

To run the services for Android support the following environment variable is required:

* ``GOOGLE_APPLICATION_CREDENTIALS``: String - Path to the firebase.json

Furthermore, the Firebase.json needs to be mounted into the Docker under the above specified path.

To run both APNS and Firebase, configure the environment variables for both.

To start Hermes, run the ``docker compose up`` command in the folder where the ``docker-compose`` file is located.

Artemis Configuration
^^^^^^^^^^^^^^^^^^^^^

The Hermes service is running on a dedicated machine and is addressed via
HTTPS. We need to extend the Artemis configuration in the file
``src/main/resources/config/application-artemis.yml`` like:

.. code:: yaml

   artemis:
     # ...
    push-notification-relay: <url>

.. _Hermes: https://github.com/ls1intum/Hermes

