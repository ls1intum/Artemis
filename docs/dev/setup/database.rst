.. _Database Setup:

Database Setup
--------------

The required Artemis schema will be created / updated automatically at startup time of the server application.
Artemis supports MySQL and PostgreSQL databases.


MySQL Setup
^^^^^^^^^^^

`Download <https://dev.mysql.com/downloads/mysql>`_ and install the MySQL Community Server (8.0.x).

You have to run a database on your local machine to be able to start Artemis.

We recommend starting the database in a docker container. You can run the MySQL Database Server
using e.g. ``docker compose -f docker/mysql.yml up``.

If you run your own MySQL server, make sure to specify the default ``character-set``
as ``utf8mb4`` and the default ``collation`` as ``utf8mb4_unicode_ci``.
You can achieve this e.g. by using a ``my.cnf`` file in the location ``/etc``.

.. code::

    [client]
    default-character-set = utf8mb4
    [mysql]
    default-character-set = utf8mb4
    [mysqld]
    character-set-client-handshake = TRUE
    init-connect='SET NAMES utf8mb4'
    character-set-server = utf8mb4
    collation-server = utf8mb4_unicode_ci

Make sure the configuration file is used by MySQL when you start the server.
You can find more information on `<https://dev.mysql.com/doc/refman/8.0/en/option-files.html>`__

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
