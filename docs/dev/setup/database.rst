.. _Database Setup:

Database Setup
--------------

Artemis supports MySQL and PostgreSQL databases.
The required Artemis schema is created or updated automatically when the server application starts.

MySQL Setup
^^^^^^^^^^^

You can set up a MySQL database for Artemis using one of the following methods:

1. **Using Docker (Recommended)**
   The easiest way to set up a MySQL database for development is by using Docker.
   Run the following command to start a MySQL database using the provided Docker Compose file:

   .. code:: sh

       docker compose -f docker/mysql.yml up

   This method simplifies setup and management, making it ideal for development environments.

2. **Using a Local MySQL Installation**
   If you prefer to install MySQL locally, download and install
   the `MySQL Community Server (9.2.x) <https://dev.mysql.com/downloads/mysql>`__ from the official MySQL website.

   When running a local MySQL server, ensure the following settings for character encoding and collation:

   - **character-set:** `utf8mb4`
   - **collation:** `utf8mb4_unicode_ci`

   These settings can be configured using a ``my.cnf`` file located in ``/etc``:

   .. code:: ini

    [client]
    default-character-set = utf8mb4
    [mysql]
    default-character-set = utf8mb4
    [mysqld]
    character-set-client-handshake = TRUE
    init-connect='SET NAMES utf8mb4'
    character-set-server = utf8mb4
    collation-server = utf8mb4_unicode_ci

   Ensure that MySQL loads this configuration file when starting the server.
   For more details, refer to the official MySQL documentation:
   `<https://dev.mysql.com/doc/refman/9.2/en/option-files.html>`__.

Users for MySQL
"""""""""""""""

| For the development environment, the default MySQL user is ‘root’ with an empty password.
| (In case you want to use a different password, make sure to change the value in
  ``application-local.yml`` *(spring > datasource > password)* and in ``liquibase.gradle``
  *(within the 'liquibaseCommand' as argument password)*).

Set empty root password for MySQL 9
"""""""""""""""""""""""""""""""""""
If you have problems connecting to the MySQL 9 database using an empty root password, you can try the following command
to reset the root password to an empty password:

.. code::

    mysql -u root --execute "ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY ''";

.. warning::
    Empty root passwords should only be used in a development environment.
    The root password for a production environment must never be empty.


PostgreSQL Setup
^^^^^^^^^^^^^^^^

No special PostgreSQL settings are required.
You can either use your package manager’s version, or set it up using a container.
An example Docker Compose setup based on the `official container image <https://hub.docker.com/_/postgres>`_
is provided in ``docker/postgres.yml``.

When setting up the Artemis server, the following values need to be added/updated in the server configuration (see setup steps below) to connect to PostgreSQL instead of MySQL:

.. code-block:: yaml

    spring:
        datasource:
            url: jdbc:postgresql://localhost:5432/Artemis?ssl=false
            username: Artemis
        jpa:
            database: POSTGRESQL

.. note::
    This example assumes that you use the mentioned Docker Compose file on your localhost, leading to a database called ``Artemis`` that runs on port ``5432`` and where no password is necessary.
    You might have to update ``spring.datasource.url`` if you use another configuration and set the password in ``spring.datasource.password``.
