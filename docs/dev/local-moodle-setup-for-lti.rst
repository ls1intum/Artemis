Local Moodle Environment Setup for LTI Development
==================================================

This guide is designed to help developers quickly establish a consistent and reliable Moodle environment for development purposes.

Prerequisites
-------------
Ensure your system meets the minimum requirements for running Docker and Docker Compose. Refer to the `official Docker documentation <https://docs.docker.com/engine/install/>`_ for system requirements.

If Docker is not installed on your system, follow the installation instructions for your specific platform on the `Docker website <https://docs.docker.com/get-docker/>`_.

Docker Compose is a separate tool bundled with Docker on some platforms, but you might need to install it separately on others. Refer to the `Docker Compose installation guide <https://docs.docker.com/compose/install/>`_ for details.

Setup Instructions
------------------
The setup process utilizes `Bitnami's Docker images for Moodle <https://github.com/bitnami/containers/tree/main/bitnami/moodle>`_ to ensure a streamlined and consistent deployment.
Bitnami offers several compelling advantages:

- Bitnami ensures timely updates with the latest bug fixes and features.
- Their consistent configuration allows seamless format transitions between containers, virtual machines, and cloud setups.
- Bitnami's images are based on a minimalist Debian container, providing a small image size while maintaining the familiarity of Debian.
- All Bitnami images on Docker Hub are signed for image integrity verification.
- Regular releases keep images up-to-date with the latest distribution packages.

How to use Bitnami images
^^^^^^^^^^^^^^^^^^^^^^^^^
Moodle requires access to a MySQL or MariaDB database to store information. Bitnami uses the Bitnami Docker Image for MariaDB for the database requirements.

Downloading the Docker Compose File
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
For Windows operating system:
Download the `docker-compose file <https://github.com/bitnami/containers/blob/main/bitnami/moodle/docker-compose.yml>`_ and run it using the below command.

.. code-block:: bash

     docker compose -f docker-compose.yml up

For other operating systems:

.. code-block:: bash

    curl -sSL https://raw.githubusercontent.com/bitnami/containers/main/bitnami/moodle/docker-compose.yml > docker-compose.yml
    docker-compose up -d

Configuration
--------------
When you start the Moodle image, you can adjust the instance's configuration by passing one or more environment variables on the docker-compose file.

Available environment variables:

- **MOODLE_USERNAME**: Moodle application username. Default: user
- **MOODLE_PASSWORD**: Moodle application password. Default: bitnami
- **MOODLE_EMAIL**: Moodle application email. Default: user@example.com
- **MOODLE_SITE_NAME**: Moodle site name. Default: New Site
- **MOODLE_SKIP_BOOTSTRAP**: Do not initialize the Moodle database for a new deployment. This is necessary in case you use a database that already has Moodle data. Default: no
- **MOODLE_HOST**: Allows you to configure Moodle's wwwroot feature. Ex: example.com. By default, it is a PHP superglobal variable. Default: `$_SERVER['HTTP_HOST']`
- **MOODLE_REVERSEPROXY**: Allows you to activate the reverseproxy feature of Moodle. Default: no
- **MOODLE_SSLPROXY**: Allows you to activate the sslproxy feature of Moodle. Default: no
- **MOODLE_LANG**: Allows you to set the default site language. Default: en

Customization
---------------
You can customize Moodle port number over the docker-compose.yml file.

.. code-block:: yaml

    moodle:
        image: bitnami/moodle:latest
        ports:
          - '80:8080'
          - '443:8443'

Accessing Moodle
------------------
You can be access your local Moodle instance over `localhost:port` in the browser.

Troubleshooting
----------------
Configuring Time Zone
^^^^^^^^^^^^^^^^^^^^^
For LTI 1.3 integration, accurate timing for tokens is crucial. To ensure correct local time, set the time zone in the `docker-compose.yml` file.

For example, for Central European Time zone:

.. code-block:: yaml

    environment:
        - TZ=Europe/Berlin

This setting ensures that all operations within the Docker container, including token generation and expiration, adhere to the Central European Time zone.
