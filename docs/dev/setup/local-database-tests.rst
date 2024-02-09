.. _local_database_tests:

Running database tests locally
---------------------------------

Prerequisites
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Database tests are the standard server integration tests executed against a real database (MySQL or Postgres) started as Docker containers. The Gradle test task will automatically pull the necessary images and start the containers.

Before running those tests, make sure you have got a working Docker setup:

1. Install `Docker Desktop <https://docs.docker.com/desktop/>`__ or `Docker Engine and Docker CLI <https://docs.docker.com/engine/install/>`__.
2. Enable `remote access for the Docker daemon <https://docs.docker.com/config/daemon/remote-access/>`__.

Executing tests from Intellij
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can execute the database tests by running the run configurations provided in the repository:

* For MySQL tests: `Artemis Server Tests MySQL`.
* For Postgres tests: `Artemis Server Tests Postgres`.

Or alternatively, by creating a custom configuration. The screenshot below presents an example of such a run configuration:

.. figure:: intellij-postgres-tests-run-configuration.png
   :align: center
   :scale: 50 %
   :alt: An example of IntelliJ run configuration for starting Postgres server tests.


Executing tests from the console
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can execute the database tests by running the dedicated Gradle command:

* For MySQL tests: `SPRING_PROFILES_INCLUDE=mysql ./gradlew cleanTest test -x webapp`.
* For Postgres tests: `SPRING_PROFILES_INCLUDE=postgres ./gradlew cleanTest test -x webapp`.
