.. _docker_compose_setup_dev:

Alternative: Docker Compose Setup
---------------------------------

Getting Started with Docker Compose
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Install `Docker Desktop <https://docs.docker.com/desktop/#docker-for-mac>`__ or
   `Docker Engine and Docker CLI with the Docker Compose Plugin <https://docs.docker.com/compose/install/>`__
   (``docker compose`` command).

   We **DON'T support** the usage of the **Compose standalone** binary (``docker-compose`` command) as its installation
   method `is no longer supported by Docker <https://docs.docker.com/compose/install/>`__.

   We recommend the latest version of Docker Desktop or Docker Engine and Docker CLI with Docker Compose Plugin.
   The minimum version for Docker Compose is 1.27.0+ as of this version the
   `latest Compose file format is supported <https://docs.docker.com/compose/compose-file/compose-versioning/#versioning>`__.

   .. hint::
     Make sure that Docker Desktop has enough memory (~ 6GB). To adapt it, go to ``Settings -> Resources``.

2. Check that all local network ports used by Docker Compose are free (e.g. you haven't started a local MySQL server
   when you would like to start a Docker Compose instance of mysql)
3. Run ``docker compose pull && docker compose up`` in the directory ``docker/``
4. Open the Artemis instance in your browser at https://localhost
5. Run ``docker compose down`` in the directory ``docker/`` to stop and remove the docker containers

.. tip::
  | The first ``docker compose pull`` command is just necessary the first time as an extra step;
    otherwise, Artemis will be built from source as you don't already have an Artemis Image locally.
  |
  | For Arm-based Macs, Dev boards, etc., you will have to build the Artemis Docker Image first, as we currently do not
    distribute Docker Images for these architectures.

Other Docker Compose Setups
^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. figure:: artemis-docker-file-structure.drawio.png
   :align: center

   Overview of the Artemis Docker / Docker Compose structure

The easiest way to configure a local deployment via Docker is a deployment with a *docker compose* file.
In the directory ``docker/`` you can find the following *docker compose* files for different **setups**:

* ``artemis-dev-mysql.yml``: **Artemis-Dev-MySQL** Setup containing the development build of Artemis and a MySQL DB
* ``artemis-dev-postgres.yml``: **Artemis-Dev-Postgres** Setup containing the development build of Artemis and
  a PostgreSQL DB
* ``artemis-prod-mysql.yml``: **Artemis-Prod-MySQL** Setup containing the production build of Artemis and a MySQL DB
* ``artemis-prod-postgres.yml``: **Artemis-Prod-Postgres** Setup containing the production build of Artemis and
  a PostgreSQL DB
* ``atlassian.yml``: **Atlassian** Setup containing a Jira, Bitbucket and Bamboo instance
  (see `Bamboo, Bitbucket and Jira Setup Guide <#bamboo-bitbucket-and-jira-setup>`__
  for the configuration of this setup)
* ``gitlab-gitlabci.yml``: **GitLab-GitLabCI** Setup containing a GitLab and GitLabCI instance
* ``gitlab-jenkins.yml``: **GitLab-Jenkins** Setup containing a GitLab and Jenkins instance
  (see `Gitlab Server Quickstart Guide <#gitlab-server-quickstart>`__ for the configuration of this setup)
* ``monitoring.yml``: **Prometheus-Grafana** Setup containing a Prometheus and Grafana instance
* ``mysql.yml``: **MySQL** Setup containing a MySQL DB instance
* ``nginx.yml``: **Nginx** Setup containing a preconfigured Nginx instance
* ``postgres.yml``: **Postgres** Setup containing a PostgreSQL DB instance

Three example commands to run such setups:

.. code:: bash

  docker compose -f docker/atlassian.yml up
  docker compose -f docker/mysql.yml -f docker/gitlab-jenkins.yml up
  docker compose -f docker/artemis-dev-postgres.yml up

.. tip::
  There is also a single ``docker-compose.yml`` in the directory ``docker/`` which mirrors the setup of ``artemis-prod-mysql.yml``.
  This should provide a quick way, without manual changes necessary, for new contributors to startup an Artemis instance.
  If the documentation just mentions to run ``docker compose`` without a ``-f <file.yml>`` argument, it's
  assumed you are running the command from the ``docker/`` directory.

For each service being used in these *docker compose* files, a **base service** (containing similar settings)
is defined in the following files:

* ``artemis.yml``: **Artemis Service**
* ``mysql.yml``: **MySQL DB Service**
* ``nginx.yml``: **Nginx Service**
* ``postgres.yml``: **PostgreSQL DB Service**
* ``gitlab.yml``: **GitLab Service**
* ``jenkins.yml``: **Jenkins Service**

For testing mails or SAML logins, you can append the following services to any setup with an artemis container:

* ``mailhog.yml``: **Mailhog Service** (email testing tool)
* ``saml-test.yml``: **Saml-Test Service** (SAML Test Identity Provider for testing SAML features)

An example command to run such an extended setup:

.. code:: bash

  docker compose -f docker/artemis-dev-mysql.yml -f docker/mailhog.yml up

.. warning::
  If you want to run multiple *docker compose* setups in parallel on one host, you might have to modify
  volume, container, and network names!

Folder structure
""""""""""""""""

| **Base services** (compose file with just one service) and **setups** (compose files with multiple services)
  should be located directly in ``docker/``.
| Additional files like configuration files, Dockerfile, ...
  should be in a subdirectory with the **base service** or **setup** name (``docker/<base service or setup name>/``).

Artemis Base Service
^^^^^^^^^^^^^^^^^^^^

Everything related to the Docker Image of Artemis (built by the Dockerfile) can be found
`in the Server Setup section <#run-the-server-via-docker>`__.
All Artemis-related settings changed in Docker Compose files are described here.

| The ``artemis.yml`` **base service** (e.g. in the ``artemis-prod-mysql.yml`` setup) defaults to the latest
  Artemis Docker Image tag in your local docker registry.
| If you want to build the checked-out version run ``docker compose build artemis-app`` before starting Artemis.
| If you want a specific version from the GitHub container registry change the ``image:`` value to the desired image
  for the ``artemis-app`` service and run ``docker compose pull artemis-app``.

Debugging with Docker
^^^^^^^^^^^^^^^^^^^^^

See the `Debugging with Docker <#docker-debugging>`__ section for detailed information.
In all development *docker compose* setups like ``artemis-dev-mysql.yml`` Java Remote Debugging is enabled by default.

Service, Container and Volume names
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Service names for the usage within *docker compose* are kept short, like ``mysql``, to make it easier
to use them in a CLI.

Container and volume names are prepended with ``artemis-`` in order to not interfere with other container or volume
names on your system.

Get a shell into the containers
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. tip::
  To keep the documentation short, we will use the standard form of ``docker compose COMMAND`` from this point on.
  You can use the following commands also with the ``-f docker/<setup to be launched>.yml`` argument pointing
  to a specific setup.

-  app container:
   ``docker compose exec artemis-app bash`` or if the container is not yet running:
   ``docker compose run --rm artemis-app bash``
-  mysql container:
   ``docker compose exec mysql bash`` or directly into mysql ``docker compose exec mysql mysql``

Analog for other services.

Other useful commands
^^^^^^^^^^^^^^^^^^^^^

- Start a setup in the background: ``docker compose up -d``
- Stop and remove containers of a setup: ``docker compose down``
- Stop, remove containers and volumes: ``docker compose down -v``
- Remove Artemis-related volumes/state: ``docker volume rm artemis-data artemis-mysql-data``

  This is helpful in setups where you just want to delete the state of artemis
  but not of Jenkins and GitLab for instance.
- Stop a service: ``docker compose stop <name of the service>`` (restart via
  ``docker compose start <name of the service>``)
- Restart a service: ``docker compose restart <name of the service>``
- Remove all local Docker containers: ``docker container rm $(docker ps -a -q)``
- Remove all local Artemis Docker images: ``docker rmi $(docker images --filter=reference="ghcr.io/ls1intum/artemis:*" -q)``
