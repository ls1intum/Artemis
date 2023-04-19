.. _dev_setup:

Setup Guide
===========

In this guide you learn how to setup the development environment of
Artemis. Artemis is based on `JHipster <https://jhipster.github.io>`__,
i.e. \ `Spring Boot <http://projects.spring.io/spring-boot>`__
development on the application server using Java 17, and TypeScript
development on the application client in the browser using
`Angular <https://angular.io>`__ and Webpack. To get an overview of the
used technology, have a look at the `JHipster Technology stack <https://jhipster.github.io/tech-stack>`__
and other tutorials on the JHipster homepage.

You can find tutorials how to setup JHipster in an IDE (`IntelliJ IDEA
Ultimate <https://www.jetbrains.com/idea>`__ is recommended) on
https://jhipster.github.io/configuring-ide. Note that the Community
Edition of IntelliJ IDEA does not provide Spring Boot support (see the
`comparison
matrix <https://www.jetbrains.com/idea/features/editions_comparison_matrix.html>`__).
Before you can build Artemis, you must install and configure the
following dependencies/tools on your machine:

1. `Java
   JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__:
   We use Java (JDK 17) to develop and run the Artemis application
   server which is based on `Spring
   Boot <http://projects.spring.io/spring-boot>`__.
2. `MySQL Database Server 8 <https://dev.mysql.com/downloads/mysql>`__, or `PostgreSQL <https://www.postgresql.org/>`_:
   Artemis uses Hibernate to store entities in an SQL database and Liquibase to
   automatically apply schema transformations when updating Artemis.
3. `Node.js <https://nodejs.org/en/download>`__: We use Node LTS (>=18.14.0 < 19) to compile
   and run the client Angular application. Depending on your system, you
   can install Node either from source or as a pre-packaged bundle.
4. `Npm <https://nodejs.org/en/download>`__: We use Npm (>=9.4.0) to
   manage client side dependencies. Npm is typically bundled with Node.js,
   but can also be installed separately.
5. ( `Graphviz <https://www.graphviz.org/download/>`__: We use Graphviz to generate graphs within exercise task
   descriptions.
   It's not necessary for a successful build,
   but it's necessary for production setups as otherwise errors will show up during runtime. )
6. ( A **version control** and **build** system is necessary for the **programming exercise** feature of Artemis.
   There are multiple stacks available for the integration with Artemis:

   * `GitLab and Jenkins <#jenkins-and-gitlab-setup>`__
   * `GitLab and GitLab CI <#gitlab-ci-and-gitlab-setup>`__ (experimental, not yet production ready)
   * `Bamboo, Bitbucket and Jira <#bamboo-bitbucket-and-jira-setup>`__)

------------------------------------------------------------------------------------------------------------------------

.. contents:: Contents of this Setup Guide
    :local:
    :depth: 1

------------------------------------------------------------------------------------------------------------------------


Database Setup
--------------

The required Artemis schema will be created / updated automatically at startup time of the server application.
Artemis supports MySQL and PostgreSQL databases.


MySQL Setup
^^^^^^^^^^^

`Download <https://dev.mysql.com/downloads/mysql>`_ and install the MySQL Community Server (8.0.x).

You have to run a database on your local machine to be able to start Artemis.

We recommend to start the database in a docker container. You can run the MySQL Database Server
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

| For the development environment the default MySQL user is ‘root’ with an empty password.
| (In case you want to use a different password, make sure to change the value in
  ``application-local.yml`` *(spring > datasource > password)* and in ``liquibase.gradle``
  *(within the 'liquibaseCommand' as argument password)*).

Set empty root password for MySQL 8
"""""""""""""""""""""""""""""""""""
If you have problems connecting to the MySQL 8 database using an empty root password, you can try the following command
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
An example Docker Compose setup based on the `official container image <https://hub.docker.com/_/postgres>`_ is provided in ``src/main/docker/postgresql.yml``.

When setting up the Artemis server, the following values need to be added/updated in the server configuration (see setup steps below) to connect to PostgreSQL instead of MySQL:

.. code-block:: yaml

    spring:
        datasource:
            url: "jdbc:postgresql://<IP/HOSTNAME of PostgreSQL database host>/Artemis?ssl=false"
            username: <YOUR_DB_USER>
            password: <YOUR_DB_PASSWORD>
        jpa:
            database-platform: org.hibernate.dialect.PostgreSQL10Dialect
            database: POSTGRESQL

.. note::
    This example assumes that the database is called ``Artemis``.
    You might have to update this part of ``spring.datasource.url`` as well if you chose a different name.

------------------------------------------------------------------------------------------------------------------------

.. _Server Setup:

Server Setup
------------

To start the Artemis application server from the development
environment, first import the project into IntelliJ and then make sure
to install the Spring Boot plugins to run the main class
``de.tum.in.www1.artemis.ArtemisApp``. Before the application runs, you
have to change some configuration options.
You can change the options directly in the file ``application-artemis.yml`` in the folder
``src/main/resources/config``. However, you have to be careful that you do not
accidentally commit your password. Therefore, we strongly recommend, to create a new file
``application-local.yml`` in the folder ``src/main/resources/config`` which is ignored by default.
You can override the following configuration options in this file.

.. code:: yaml

   artemis:
       repo-clone-path: ./repos/
       repo-download-clone-path: ./repos-download/
       bcrypt-salt-rounds: 11   # The number of salt rounds for the bcrypt password hashing. Lower numbers make it faster but more unsecure and vice versa.
                                # Please use the bcrypt benchmark tool to determine the best number of rounds for your system. https://github.com/ls1intum/bcrypt-Benchmark
       user-management:
           use-external: true
           password-reset:
                credential-provider: <provider> # The credential provider which users can log in though (e.g. TUMonline)
                links: # The password reset links for different languages
                    en: '<link>'
                    de: '<link>'
           external:
               url: https://jira.ase.in.tum.de
               user: <username>    # e.g. ga12abc
               password: <password>
               admin-group-name: tumuser
           ldap:
               url: <url>
               user-dn: <user-dn>
               password: <password>
               base: <base>
       version-control:
           url: https://bitbucket.ase.in.tum.de
           user: <username>    # e.g. ga12abc
           password: <password>
           token: <token>                 # VCS API token giving Artemis full Admin access. Not needed for Bamboo+Bitbucket
           ci-token: <token from the CI>   # Token generated by the CI (e.g. Jenkins) for webhooks from the VCS to the CI. Not needed for Bamboo+Bitbucket
       continuous-integration:
           url: https://bamboo.ase.in.tum.de
           user: <username>    # e.g. ga12abc
           token: <token>      # Enter a valid token generated by bamboo or leave this empty to use the fallback authentication user + password
           password: <password>
           vcs-application-link-name: LS1 Bitbucket Server     # If the VCS and CI are directly linked (normally only for Bitbucket + Bamboo)
           empty-commit-necessary: true                        # Do we need an empty commit for new exercises/repositories in order for the CI to register the repo
           # Hash/key of the ci-token, equivalent e.g. to the ci-token in version-control
           # Some CI systems, like Jenkins, offer a specific token that gets checked against any incoming notifications
           # from a VCS trying to trigger a build plan. Only if the notification request contains the correct token, the plan
           # is triggered. This can be seen as an alternative to sending an authenticated request to a REST API and then
           # triggering the plan.
           # In the case of Artemis, this is only really needed for the Jenkins + GitLab setup, since the GitLab plugin in
           # Jenkins only allows triggering the Jenkins jobs using such a token. Furthermore, in this case, the value of the
           # hudson.util.Secret is stored in the build plan, so you also have to specify this encrypted string here and NOT the actual token value itself!
           # You can get this by GETting any job.xml for a job with an activated GitLab step and your token value of choice.
           secret-push-token: <token hash>
           # Key of the saved credentials for the VCS service
           # Bamboo: not needed
           # Jenkins: You have to specify the key from the credentials page in Jenkins under which the user and
           #          password for the VCS are stored
           vcs-credentials: <credentials key>
           # Key of the credentials for the Artemis notification token
           # Bamboo: not needed
           # Jenkins: You have to specify the key from the credentials page in Jenkins under which the notification token is stored
           notification-token: <credentials key>
           # The actual value of the notification token to check against in Artemis. This is the token that gets send with
           # every request the CI system makes to Artemis containing a new result after a build.
           # Bamboo: The token value you use for the Server Notification Plugin
           # Jenkins: The token value you use for the Server Notification Plugin and is stored under the notification-token credential above
           authentication-token: <token>
       git:
           name: Artemis
           email: artemis@in.tum.de
       athene:
           url: http://localhost
           base64-secret: YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=
           token-validity-in-seconds: 10800

Change all entries with ``<...>`` with proper values, e.g. your TUM
Online account credentials to connect to the given instances of JIRA,
Bitbucket and Bamboo. Alternatively, you can connect to your local JIRA,
Bitbucket and Bamboo instances. It’s not necessary to fill all the
fields, most of them can be left blank. Note that there is additional
information about the setup for programming exercises provided:

.. note::
   Be careful that you do not commit changes to ``application-artemis.yml``.
   To avoid this, follow the best practice when configuring your local development environment:

   1) Create a file named ``application-local.yml`` under ``src/main/resources/config``.
   2) Copy the contents of ``application-artemis.yml`` into the new file.
   3) Update configuration values in ``application-local.yml``.

   By default, changes to ``application-local.yml`` will be ignored by git so you don't accidentally
   share your credentials or other local configuration options. The run configurations contain a profile
   ``local`` at the end to make sure the ``application-local.yml`` is considered. You can create your own
   configuration files ``application-<name>.yml`` and then activate the profile ``<name>`` in the run
   configuration if you need additional customizations.

If you use a password, you need to adapt it in
``gradle/liquibase.gradle``.


Run the server via a service configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This setup is recommended for production instances as it registers Artemis as a service and e.g. enables auto-restarting
of Artemis after the VM running Artemis has been restarted.
As alternative you could take a look at the section below about
`deploying artemis as docker container <#run-the-server-via-docker>`__.
For development setups, see the other guides below.

This is a service file that works on Debian/Ubuntu (using systemd):

::

   [Unit]
   Description=Artemis
   After=syslog.target
   [Service]
   User=artemis
   WorkingDirectory=/opt/artemis
   ExecStart=/usr/bin/java \
     -Djdk.tls.ephemeralDHKeySize=2048 \
     -DLC_CTYPE=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Dsun.jnu.encoding=UTF-8 \
     -Djava.security.egd=file:/dev/./urandom \
     -Xmx2048m \
     --add-modules java.se \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.management/sun.management=ALL-UNNAMED \
     --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
     -jar artemis.war \
     --spring.profiles.active=prod,bamboo,bitbucket,jira,ldap,scheduling,openapi
   SuccessExitStatus=143
   StandardOutput=/opt/artemis/artemis.log
   StandardError=inherit
   [Install]
   WantedBy=multi-user.target


The following parts might also be useful for other (production) setups, even if this service file is not used:

- ``-Djava.security.egd=file:/dev/./urandom``: This is required if repositories are cloned via SSH from the VCS.
   The default (pseudo-)random-generator ``/dev/random`` is blocking which results in very bad performance when using
   SSH due to lack of entropy.


The file should be placed at ``/etc/systemd/system/artemis.service`` and after running ``sudo systemctl daemon-reload``,
you can start the service using ``sudo systemctl start artemis``.

You can stop the service using ``sudo service artemis stop`` and restart it using ``sudo service artemis restart``.

Logs can be fetched using ``sudo journalctl -u artemis -f -n 200``.

Run the server via Docker
^^^^^^^^^^^^^^^^^^^^^^^^^

| Artemis provides a Docker image named ``ghcr.io/ls1intum/artemis:<TAG/VERSION>``.
| The current develop branch is provided by the tag ``develop``.
| The latest release is provided by the tag ``latest``.
| Specific releases like ``5.7.1`` can be retrieved as ``ghcr.io/ls1intum/artemis:5.7.1``.
| Branches tied to a pull request can be obtained by using the tag ``PR-<PR NUMBER>``.


Dockerfile
""""""""""

You can find the latest Artemis Dockerfile at ``docker/artemis/Dockerfile``.

* The Dockerfile has `multiple stages <https://docs.docker.com/build/building/multi-stage/>`__: A **builder** stage,
  building the ``.war`` file, an optional **external_builder** stage to import a pre-built ``.war`` file,
  a **war_file** stage to choose between the builder stages via build argument and a **runtime** stage with minimal
  dependencies just for running artemis.

* The Dockerfile defines three Docker volumes (at the specified paths inside the container):

    * **/opt/artemis/config:**

      This can be used to store additional configuration of Artemis in YAML files.
      The usage is optional and we recommend using the environment files for overriding your custom configurations
      instead of using ``src/main/resources/application-local.yml`` as such an additional configuration file.
      The other configurations like ``src/main/resources/application.yml``, ... are built into the ``.war`` file and
      therefore are not needed in this directory.

      .. tip::
        Instead of mounting this config directory, you can also use environment variables for the configuration as
        defined by the
        `Spring relaxed binding <https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0#environment-variables>`__.
        You can either place those environment variables directly in the ``environment`` section,
        or create an `.env-file <https://docs.docker.com/compose/environment-variables/#the-env-file>`__.
        When starting an Artemis container directly with the Docker-CLI, an .env-file can also be given via the
        ``--env-file`` option.

        To ease the transition of an existing set of YAML configuration files into the environment variable style, a
        `helper script <https://github.com/b-fein/spring-yaml-to-env>`__ can be used.

    * **/opt/artemis/data:**

      This directory should be used for any data (e.g., local clone of repositories).
      This is preconfigured in the ``docker`` Java Spring profile (which sets the following values:
      ``artemis.repo-clone-path``, ``artemis.repo-download-clone-path``,
      ``artemis.course-archives-path``, ``artemis.submission-export-path``, and ``artemis.file-upload-path``).

    * **/opt/artemis/public/content:**

      This directory will be used for branding.
      You can specify a favicon, ``imprint.html``, and ``privacy_statement.html`` here.

* The Dockerfile assumes that the mounted volumes are located on a file system with the following locale settings
  (see `#4439 <https://github.com/ls1intum/Artemis/issues/4439>`__ for more details):

    * LC_ALL ``en_US.UTF-8``
    * LANG ``en_US.UTF-8``
    * LANGUAGE ``en_US.UTF-8``

.. _Docker Debugging:

Debugging with Docker
"""""""""""""""""""""

| The Docker containers have the possibility to enable Java Remote Debugging via Java environment variables.
| Java Remote Debugging allows you to use your preferred debugger connected to port 5005.
  For IntelliJ you can use the `Remote Java Debugging for Docker` profile being shipped in the git repository.

With the following Java environment variable you can configure the Remote Java Debugging inside a container:

::

   _JAVA_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

| This is already preset in the Docker Compose **Artemis-Dev-MySQL** Setup.
| For issues at the startup you might have to suspend the java command until a Debugger connected.
  This is possible by setting ``suspend=y``.


Run the server via a run configuration in IntelliJ
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The project comes with some pre-configured run / debug configurations that are stored in the ``.idea`` directory.
When you import the project into IntelliJ the run configurations will also be imported.

The recommended way is to run the server and the client separated. This provides fast rebuilds of the server and hot
module replacement in the client.

* **Artemis (Server):** The server will be started separated from the client. The startup time decreases significantly.
* **Artemis (Client):** Will execute ``npm install`` and ``npm run serve``. The client will be available at
  `http://localhost:9000/ <http://localhost:9000/>`__ with hot module replacement enabled (also see
  `Client Setup <#client-setup>`__).

Other run / debug configurations
""""""""""""""""""""""""""""""""

* **Artemis (Server & Client):** Will start the server and the client. The client will be available at
  `http://localhost:8080/ <http://localhost:8080/>`__ with hot module replacement disabled.
* **Artemis (Server, Jenkins & GitLab):** The server will be started separated from the client with the profiles
  ``dev,jenkins,gitlab,artemis`` instead of ``dev,bamboo,bitbucket,jira,artemis``.
* **Artemis (Server, Athene):** The server will be started separated from the client with ``athene`` profile enabled
  (see `Athene Service <#athene-service>`__).

Run the server with Spring Boot and Spring profiles
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Artemis server should startup by running the main class
``de.tum.in.www1.artemis.ArtemisApp`` using Spring Boot.

.. note::
    Artemis uses Spring profiles to segregate parts of the
    application configuration and make it only available in certain
    environments. For development purposes, the following program arguments
    can be used to enable the ``dev`` profile and the profiles for JIRA,
    Bitbucket and Bamboo:

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling

If you use IntelliJ (Community or Ultimate) you can set the active
profiles by

* Choosing ``Run | Edit Configurations...``
* Going to the ``Configuration Tab``
* Expanding the ``Environment`` section to reveal ``VM Options`` and setting them to
  ``-Dspring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling``

Set Spring profiles with IntelliJ Ultimate
""""""""""""""""""""""""""""""""""""""""""

If you use IntelliJ Ultimate, add the following entry to the section
``Active Profiles`` (within ``Spring Boot``) in the server run
configuration:

::

   dev,bamboo,bitbucket,jira,artemis,scheduling

Run the server with the command line (Gradle wrapper)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you want to run the application via the command line instead, make
sure to pass the active profiles to the ``gradlew`` command like this:

::

   ./gradlew bootRun --args='--spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling'

As an alternative, you might want to use Jenkins and GitLab with an
internal user management in Artemis, then you would use the profiles:

::

   dev,jenkins,gitlab,artemis,scheduling



------------------------------------------------------------------------------------------------------------------------

Client Setup
------------

You need to install Node and Npm on your local machine.

Using IntelliJ
^^^^^^^^^^^^^^

If you are using **IntelliJ** you can use the pre-configured ``Artemis (Client)``
run configuration that will be delivered with this repository:

* Choose ``Run | Edit Configurations...``
* Select the ``Artemis (Client)`` configuration from the ``npm section``
* Now you can run the configuration in the upper right corner of IntelliJ

.. _UsingTheCommandLine:

Using the command line
^^^^^^^^^^^^^^^^^^^^^^

You should be able to run the following
command to install development tools and dependencies. You will only
need to run this command when dependencies change in ``package.json``.

::

   npm install

To start the client application in the browser, use the following
command:

::

   npm run serve

This compiles TypeScript code to JavaScript code, starts the hot module
replacement feature in Webpack (i.e. whenever you change a TypeScript
file and save, the client is automatically reloaded with the new code)
and will start the client application in your browser on
``http://localhost:9000``. If you have activated the JIRA profile (see
above in `Server Setup <#server-setup>`__) and if you have configured
``application-artemis.yml`` correctly, then you should be able to login
with your TUM Online account.

.. HINT::
   In case you encounter any problems regarding JavaScript heap memory leaks when executing ``npm run serve`` or
   any other scripts from ``package.json``, you can adjust a
   `memory limit parameter <https://nodejs.org/docs/latest-v16.x/api/cli.html#--max-old-space-sizesize-in-megabytes>`__
   (``node-options=--max-old-space-size=6144``) which is set by default in the project-wide `.npmrc` file.

   If you still face the issue, you can try to set a lower/higher value than 6144 MB.
   Recommended values are 3072 (3GB), 4096 (4GB), 5120 (5GB) , 6144 (6GB), 7168 (7GB), and 8192 (8GB).

   You can override the project-wide `.npmrc` file by
   `using a per-user config file (~/.npmrc) <https://docs.npmjs.com/cli/v8/configuring-npm/npmrc>`__.

   Make sure to **not commit changes** in the project-wide ``.npmrc`` unless the Github build also needs these settings.


For more information, review `Working with
Angular <https://www.jhipster.tech/development/#working-with-angular>`__.
For further instructions on how to develop with JHipster, have a look at
`Using JHipster in
development <http://www.jhipster.tech/development>`__.

------------------------------------------------------------------------------------------------------------------------

Customize your Artemis instance
-------------------------------

You can define the following custom assets for Artemis to be used
instead of the TUM defaults:

* The logo next to the “Artemis” heading on the navbar → ``${artemisRunDirectory}/public/images/logo.png``
* The favicon → ``${artemisRunDirectory}/logo/favicon.svg``
* The privacy statement HTML → ``${artemisRunDirectory}/public/content/privacy_statement.html``
* The imprint statement HTML → ``${artemisRunDirectory}/public/content/imprint.html``
* The contact email address in the ``application-{dev,prod}.yml`` configuration file under the key ``info.contact``

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/bamboo-bitbucket-jira.rst

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/jenkins-gitlab.rst

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/gitlabci-gitlab.rst

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/common-problems.rst

------------------------------------------------------------------------------------------------------------------------

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
  | The first ``docker compose pull`` command is just necessary the first time as an extra step,
    as otherwise Artemis will be built from source as you don't already have an Artemis Image locally.
  |
  | For Arm-based Macs, Dev boards, etc. you will have to build the Artemis Docker Image first as we currently do not
    distribute Docker Images for these architectures.

Other Docker Compose Setups
^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. figure:: setup/artemis-docker-file-structure.drawio.png
   :align: center
   :target: ../../_images/artemis-docker-file-structure.drawio.png

   Overview of the Artemis Docker / Docker Compose structure

The easiest way to configure a local deployment via Docker is a deployment with a *docker compose* file.
In the directory ``docker/`` you can find the following *docker compose* files for different **setups**:

* ``artemis-dev-mysql.yml``: **Artemis-Dev-MySQL** Setup containing the development build of Artemis and a MySQL DB
* ``artemis-prod-mysql.yml``: **Artemis-Prod-MySQL** Setup containing the production build of Artemis and a MySQL DB
* ``atlassian.yml``: **Atlassian** Setup containing a Jira, Bitbucket and Bamboo instance
  (see `Bamboo, Bitbucket and Jira Setup Guide <#bamboo-bitbucket-and-jira-setup>`__
  for the configuration of this setup)
* ``gitlab-gitlabci.yml``: **GitLab-GitLabCI** Setup containing a GitLab and GitLabCI instance
* ``gitlab-jenkins.yml``: **GitLab-Jenkins** Setup containing a GitLab and Jenkins instance
  (see `Gitlab Server Quickstart Guide <#gitlab-server-quickstart>`__ for the configuration of this setup)
* ``monitoring.yml``: **Prometheus-Grafana** Setup containing a Prometheus and Grafana instance
* ``mysql.yml``: **MySQL** Setup containing a MySQL DB instance
* ``nginx.yml``: **Nginx** Setup containing a preconfigured Nginx instance
* ``postgresql.yml``: **PostgreSQL** Setup containing a PostgreSQL DB instance

Two example commands to run such setups:

.. code:: bash

  docker compose -f docker/atlassian.yml up
  docker compose -f docker/mysql.yml -f docker/gitlab-jenkins.yml up

.. tip::
  There is also a single ``docker-compose.yml`` in the directory ``docker/`` which mirrors the setup of ``artemis-prod-mysql.yml``.
  This should provide a quick way, without manual changes necessary, for new contributors to startup an Artemis instance.
  If the documentation just mentions to run ``docker compose`` without a ``-f <file.yml>`` argument, it's
  assumed you are running the command from the ``docker/`` directory.F

For each service being used in these *docker compose* files a **base service** (containing similar settings)
is defined in the following files:

* ``artemis.yml``: **Artemis Service**
* ``mysql.yml``: **MySQL DB Service**
* ``nginx.yml``: **Nginx Service**
* ``postgresql.yml``: **PostgreSQL DB Service**
* ``gitlab.yml``: **GitLab Service**
* ``jenkins.yml``: **Jenkins Service**

For testing mails or SAML logins you can append the following services to any setup with an artemis container:

* ``mailhog.yml``: **Mailhog Service** (email testing tool)
* ``saml-test.yml``: **Saml-Test Service** (SAML Test Identity Provider for testing SAML features)

An example command to run such an extended setup:

.. code:: bash

  docker compose -f docker/artemis-dev-mysql.yml -f docker/mailhog.yml up

.. warning::
  If you want to run multiple *docker compose* setups in parallel on one host you might have to modify
  volume, container and network names!

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
All Artemis related settings changed in Docker compose files are described here.

| The ``artemis.yml`` **base service** (e.g. in the ``artemis-prod-mysql.yml`` setup) defaults to the latest
  Artemis Docker Image tag in your local docker registry.
| If you want to build the checked out version run ``docker compose build artemis-app`` before starting Artemis.
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
- Remove artemis related volumes/state: ``docker volume rm artemis-data artemis-mysql-data``

  This is helpful in setups where you just want to delete the state of artemis
  but not of Jenkins and GitLab for instance.
- Stop a service: ``docker compose stop <name of the service>`` (restart via
  ``docker compose start <name of the service>``)
- Restart a service: ``docker compose restart <name of the service>``
- Remove all local Docker containers: ``docker container rm $(docker ps -a -q)``
- Remove all local Artemis Docker images: ``docker rmi $(docker images -q ghcr.io/ls1intum/artemis)``
